package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ThresholdBand;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Phase 2 evaluation harness. Ground truth is read only here; never referenced by runtime reconciliation. */
public class Phase2Evaluation {

    // Anomaly classes where a true counterpart exists but the deterministic link is lost, so probabilistic
    // recovery (reconciliation) is the correct outcome. None of these are produced by the Phase 1 generator.
    private static final Set<String> LOST_LINK_RECOVERABLE_TYPES = Set.of("REFERENCE_MISMATCH", "ORPHAN_REVERSAL");

    public RecoverableGate recoverableGate(List<CanonicalEvent> canonical, List<GroundTruthRecord> groundTruth) {
        Map<String, String> anomalyByInstruction = new HashMap<>();
        for (GroundTruthRecord record : groundTruth) {
            anomalyByInstruction.put(record.instructionId(), record.anomalyType());
        }

        List<CanonicalEvent> unresolvedOriginators = canonical.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(this::isUnresolved)
            .toList();

        Map<String, Integer> byAnomaly = new TreeMap<>();
        int surfaced = 0;
        int confirmationEligible = 0;
        int counterpartPresent = 0;
        int lostLink = 0;
        for (CanonicalEvent originator : unresolvedOriginators) {
            String anomaly = anomalyByInstruction.getOrDefault(originator.getBusinessEventId(), "NONE(reversed-or-clean)");
            byAnomaly.merge(anomaly, 1, Integer::sum);
            if (originator.getMatchingState() == MatchingState.PROBABLE_MATCH) {
                surfaced++;
                ProbableMatchEvidence evidence = originator.getProbableMatchEvidence();
                if (evidence != null && evidence.thresholdBand() == ThresholdBand.CONFIRMATION_ELIGIBLE) {
                    confirmationEligible++;
                }
            }
            if (anomaly.equals("AMOUNT_MISMATCH") || anomaly.equals("STATUS_MISMATCH")) {
                counterpartPresent++;
            }
            if (LOST_LINK_RECOVERABLE_TYPES.contains(anomaly)) {
                lostLink++;
            }
        }
        return new RecoverableGate(unresolvedOriginators.size(), surfaced, confirmationEligible,
            counterpartPresent, lostLink, byAnomaly);
    }

    private boolean isUnresolved(CanonicalEvent event) {
        MatchingState matchingState = event.getMatchingState();
        ReconciliationState reconciliationState = event.getReconciliationState();
        boolean matched = matchingState == MatchingState.EXACT_MATCH
            || matchingState == MatchingState.COMPOSITE_MATCH
            || matchingState == MatchingState.PROBABLE_MATCH_CONFIRMED
            || reconciliationState == ReconciliationState.MATCHED;
        boolean pending = matchingState == MatchingState.TIMING_DIFFERENCE_PENDING
            || reconciliationState == ReconciliationState.PENDING;
        return !matched && !pending;
    }

    /** Precision/recall of probabilistic recovery, measured only on the REFERENCE_MISMATCH population that should be recovered. */
    public Phase2Scorecard evaluate(long seed, List<CanonicalEvent> canonical, List<GroundTruthRecord> groundTruth, double confirmationThreshold) {
        Map<String, String> anomalyByInstruction = new HashMap<>();
        for (GroundTruthRecord record : groundTruth) {
            anomalyByInstruction.put(record.instructionId(), record.anomalyType());
        }
        List<CanonicalEvent> unresolved = canonical.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(this::isUnresolved)
            .toList();

        int referenceMismatchTotal = 0;
        BigDecimal referenceMismatchInr = BigDecimal.ZERO;
        for (CanonicalEvent event : unresolved) {
            if ("REFERENCE_MISMATCH".equals(anomalyByInstruction.get(event.getBusinessEventId()))) {
                referenceMismatchTotal++;
                referenceMismatchInr = referenceMismatchInr.add(event.getAmount() == null ? BigDecimal.ZERO : event.getAmount());
            }
        }

        List<PrecisionRecall> sensitivity = new ArrayList<>();
        for (double threshold : new double[] {0.50, 0.60, 0.70, 0.80, 0.90}) {
            sensitivity.add(precisionRecall(unresolved, anomalyByInstruction, referenceMismatchTotal, threshold));
        }
        PrecisionRecall atConfirmation = precisionRecall(unresolved, anomalyByInstruction, referenceMismatchTotal, confirmationThreshold);

        return new Phase2Scorecard(seed, unresolved.size(), referenceMismatchTotal, referenceMismatchInr,
            atConfirmation, sensitivity, scoreDistribution(unresolved));
    }

    private PrecisionRecall precisionRecall(List<CanonicalEvent> unresolved, Map<String, String> anomaly, int referenceMismatchTotal, double threshold) {
        int truePositives = 0;
        int falsePositives = 0;
        for (CanonicalEvent event : unresolved) {
            ProbableMatchEvidence evidence = event.getProbableMatchEvidence();
            boolean promoted = event.getMatchingState() == MatchingState.PROBABLE_MATCH
                && evidence != null && evidence.probableScore() >= threshold;
            if (!promoted) {
                continue;
            }
            boolean correct = "REFERENCE_MISMATCH".equals(anomaly.get(event.getBusinessEventId()))
                && indexOf(event.getBusinessEventId()) == indexOf(evidence.matchedCandidateReference());
            if (correct) {
                truePositives++;
            } else {
                falsePositives++;
            }
        }
        int falseNegatives = referenceMismatchTotal - truePositives;
        double precision = (truePositives + falsePositives) == 0 ? 0.0 : (double) truePositives / (truePositives + falsePositives);
        double recall = referenceMismatchTotal == 0 ? 0.0 : (double) truePositives / referenceMismatchTotal;
        return new PrecisionRecall(threshold, truePositives, falsePositives, falseNegatives, precision, recall);
    }

    private Map<String, Integer> scoreDistribution(List<CanonicalEvent> unresolved) {
        Map<String, Integer> distribution = new TreeMap<>();
        for (String bucket : new String[] {"[0.5,0.6)", "[0.6,0.7)", "[0.7,0.8)", "[0.8,0.9)", "[0.9,1.0]", "unsurfaced(<0.5)"}) {
            distribution.put(bucket, 0);
        }
        for (CanonicalEvent event : unresolved) {
            distribution.merge(bucketOf(event.getProbableMatchEvidence()), 1, Integer::sum);
        }
        return distribution;
    }

    private String bucketOf(ProbableMatchEvidence evidence) {
        if (evidence == null) {
            return "unsurfaced(<0.5)";
        }
        double score = evidence.probableScore();
        if (score < 0.6) {
            return "[0.5,0.6)";
        }
        if (score < 0.7) {
            return "[0.6,0.7)";
        }
        if (score < 0.8) {
            return "[0.7,0.8)";
        }
        if (score < 0.9) {
            return "[0.8,0.9)";
        }
        return "[0.9,1.0]";
    }

    private int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(id);
        return matcher.find() ? Integer.parseInt(matcher.group()) : -2;
    }

    public record RecoverableGate(
        int unresolvedOriginators,
        int surfacedProbableMatches,
        int confirmationEligible,
        int counterpartPresentDiscrepancies,
        int lostLinkRecoverablePairs,
        Map<String, Integer> byGroundTruthAnomaly
    ) {
        public String render() {
            return "=== Phase 2 recoverable-pair gate ===\n"
                + "unresolvedOriginators=" + unresolvedOriginators + "\n"
                + "surfacedProbableMatches=" + surfacedProbableMatches + "\n"
                + "confirmationEligible=" + confirmationEligible + "\n"
                + "counterpartPresentDiscrepancies(amount/status,stay-exceptions)=" + counterpartPresentDiscrepancies + "\n"
                + "lostLinkRecoverablePairs(reference-mismatch/orphan-reversal)=" + lostLinkRecoverablePairs + "\n"
                + "byGroundTruthAnomaly=" + byGroundTruthAnomaly + "\n";
        }
    }

    public record PrecisionRecall(
        double threshold, int truePositives, int falsePositives, int falseNegatives, double precision, double recall
    ) {
        public String render() {
            return String.format(Locale.US, "t=%.2f TP=%d FP=%d FN=%d precision=%.4f recall=%.4f",
                threshold, truePositives, falsePositives, falseNegatives, precision, recall);
        }
    }

    public record Phase2Scorecard(
        long seed,
        int unresolvedOriginators,
        int referenceMismatchTotal,
        BigDecimal referenceMismatchInr,
        PrecisionRecall atConfirmationThreshold,
        List<PrecisionRecall> thresholdSensitivity,
        Map<String, Integer> scoreDistribution
    ) {
        public String render() {
            StringBuilder builder = new StringBuilder();
            builder.append("=== Phase 2 precision/recall (seed ").append(seed).append(") ===\n");
            builder.append("unresolvedOriginators=").append(unresolvedOriginators).append("\n");
            builder.append("referenceMismatchTotal=").append(referenceMismatchTotal).append("\n");
            builder.append("referenceMismatchInr=").append(referenceMismatchInr.toPlainString()).append("\n");
            builder.append("atConfirmationThreshold ").append(atConfirmationThreshold.render()).append("\n");
            builder.append("thresholdSensitivity:\n");
            for (PrecisionRecall pr : thresholdSensitivity) {
                builder.append("  ").append(pr.render()).append("\n");
            }
            builder.append("scoreDistribution=").append(scoreDistribution).append("\n");
            return builder.toString();
        }
    }
}
