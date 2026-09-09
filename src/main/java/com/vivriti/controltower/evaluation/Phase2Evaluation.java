package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ThresholdBand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
}
