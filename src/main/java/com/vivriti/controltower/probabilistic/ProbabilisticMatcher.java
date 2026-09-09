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
        List<CanonicalEvent> candidates = events.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.LMS || event.getSourceSystem() == SourceSystem.BANK)
            .filter(this::isUnresolvedCandidate)
            .toList();
        List<ProbableMatch> surfaced = new ArrayList<>();
        for (CanonicalEvent originator : events) {
            if (originator.getSourceSystem() != SourceSystem.ORIGINATOR || !isUnresolvedCandidate(originator)) {
                continue;
            }
            CanonicalEvent bestCandidate = null;
            ProbableMatchEvidence bestEvidence = null;
            for (CanonicalEvent candidate : candidates) {
                ProbableMatchEvidence evidence = evaluate(originator, candidate);
                if (isBetter(evidence, candidate, bestEvidence, bestCandidate)) {
                    bestCandidate = candidate;
                    bestEvidence = evidence;
                }
            }
            if (bestEvidence != null && bestEvidence.probableScore() >= config.surfaceThreshold()) {
                originator.setMatchingState(MatchingState.PROBABLE_MATCH);
                originator.setProbableMatchEvidence(bestEvidence);
                surfaced.add(new ProbableMatch(originator, bestCandidate, bestEvidence));
            }
        }
        return List.copyOf(surfaced);
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
