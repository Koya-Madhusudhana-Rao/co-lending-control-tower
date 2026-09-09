package com.vivriti.controltower.probabilistic;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.FieldScores;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ThresholdBand;

import java.util.ArrayList;
import java.util.List;

/** Level 4 probabilistic scorer: pairs post-L1/2/3 unresolved originators against unresolved LMS/Bank events. */
public class ProbabilisticMatcher {

    private final ProbabilisticMatchConfig config;

    public ProbabilisticMatcher(ProbabilisticMatchConfig config) {
        this.config = config;
    }

    /** Annotates unresolved originators reaching surfaceThreshold and returns the surfaced matches. No-op and no mutation when disabled. */
    public List<ProbableMatch> score(List<CanonicalEvent> events) {
        if (!config.enabled()) {
            return List.of();
        }
        // Candidacy filter: unresolved LMS/Bank legs that are not reversed and carry no reversal linkage.
        List<CanonicalEvent> candidates = events.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.LMS || event.getSourceSystem() == SourceSystem.BANK)
            .filter(this::isUnresolvedCandidate)
            .filter(this::isEligibleLeg)
            .toList();
        List<ProbableMatch> surfaced = new ArrayList<>();
        for (CanonicalEvent originator : events) {
            if (originator.getSourceSystem() != SourceSystem.ORIGINATOR || !isUnresolvedCandidate(originator)) {
                continue;
            }
            // Group completeness mirrors Level 1: require an eligible LMS leg and an eligible Bank leg that each reach
            // the completeness threshold, above the lexical-adjacency noise floor of sequential IDs (a spurious adjacent leg scores ~0.58).
            Scored bestLms = bestOf(originator, candidates, SourceSystem.LMS);
            Scored bestBank = bestOf(originator, candidates, SourceSystem.BANK);
            if (bestLms == null || bestBank == null
                || bestLms.evidence().probableScore() < config.completenessThreshold()
                || bestBank.evidence().probableScore() < config.completenessThreshold()) {
                continue;
            }
            Scored best = isBetter(bestBank.evidence(), bestBank.candidate(), bestLms.evidence(), bestLms.candidate())
                ? bestBank : bestLms;
            originator.setMatchingState(MatchingState.PROBABLE_MATCH);
            originator.setProbableMatchEvidence(best.evidence());
            surfaced.add(new ProbableMatch(originator, best.candidate(), best.evidence()));
        }
        return List.copyOf(surfaced);
    }

    private Scored bestOf(CanonicalEvent originator, List<CanonicalEvent> candidates, SourceSystem source) {
        CanonicalEvent bestCandidate = null;
        ProbableMatchEvidence bestEvidence = null;
        for (CanonicalEvent candidate : candidates) {
            if (candidate.getSourceSystem() != source) {
                continue;
            }
            ProbableMatchEvidence evidence = evaluate(originator, candidate);
            if (isBetter(evidence, candidate, bestEvidence, bestCandidate)) {
                bestCandidate = candidate;
                bestEvidence = evidence;
            }
        }
        return bestCandidate == null ? null : new Scored(bestCandidate, bestEvidence);
    }

    /** A candidate leg is ineligible if it is reversed or carries reversal linkage; deterministic, never scored. */
    private boolean isEligibleLeg(CanonicalEvent leg) {
        if ("REVERSED".equals(leg.getSourceStatus())) {
            return false;
        }
        String reversalReference = leg.getReversalReference();
        return reversalReference == null || reversalReference.isBlank();
    }

    private record Scored(CanonicalEvent candidate, ProbableMatchEvidence evidence) {
    }

    /** Pure scoring of one candidate pair; independent of the enabled flag and free of side effects. */
    public ProbableMatchEvidence evaluate(CanonicalEvent originator, CanonicalEvent candidate) {
        double reference = SimilarityFunctions.referenceSimilarity(originator, candidate);
        double amount = SimilarityFunctions.amountProximity(originator.getAmount(), candidate.getAmount(), config.amountBandInr());
        double timestamp = SimilarityFunctions.timestampProximity(originator.getSourceTimestamp(), candidate.getSourceTimestamp(), config.timeBandHours());
        boolean partnerComparable = SimilarityFunctions.partnerComparable(originator, candidate);
        double partner = partnerComparable ? SimilarityFunctions.partnerAgreement(originator, candidate) : 0.0;

        // Renormalize over the components actually compared: an uncomparable partner is excluded, not scored 0.
        double partnerWeight = partnerComparable ? config.partnerWeight() : 0.0;
        double weighted = config.referenceWeight() * reference
            + config.amountWeight() * amount
            + config.timestampWeight() * timestamp
            + partnerWeight * partner;
        double denominator = config.referenceWeight() + config.amountWeight() + config.timestampWeight() + partnerWeight;
        double score = denominator == 0.0 ? 0.0 : weighted / denominator;
        ThresholdBand band = ThresholdBand.classify(score, config.surfaceThreshold(), config.confirmationThreshold());
        return new ProbableMatchEvidence(score, new FieldScores(reference, amount, timestamp, partner),
            candidate.getImmutableSourceRecordId(), band, config.surfaceThreshold(), config.confirmationThreshold());
    }

    private boolean isBetter(ProbableMatchEvidence candidateEvidence, CanonicalEvent candidate,
                             ProbableMatchEvidence bestEvidence, CanonicalEvent best) {
        if (bestEvidence == null) {
            return true;
        }
        if (candidateEvidence.probableScore() > bestEvidence.probableScore()) {
            return true;
        }
        if (candidateEvidence.probableScore() < bestEvidence.probableScore()) {
            return false;
        }
        return idOf(candidate).compareTo(idOf(best)) < 0;
    }

    private String idOf(CanonicalEvent event) {
        return event.getImmutableSourceRecordId() == null ? "" : event.getImmutableSourceRecordId();
    }

    private boolean isUnresolvedCandidate(CanonicalEvent event) {
        MatchingState matchingState = event.getMatchingState();
        ReconciliationState reconciliationState = event.getReconciliationState();
        boolean matched = matchingState == MatchingState.EXACT_MATCH
            || matchingState == MatchingState.COMPOSITE_MATCH
            || reconciliationState == ReconciliationState.MATCHED;
        boolean pending = matchingState == MatchingState.TIMING_DIFFERENCE_PENDING
            || reconciliationState == ReconciliationState.PENDING;
        boolean alreadyProbable = matchingState == MatchingState.PROBABLE_MATCH;
        return !matched && !pending && !alreadyProbable;
    }
}
