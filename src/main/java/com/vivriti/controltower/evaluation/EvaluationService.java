package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.exceptions.ExceptionRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Computes per-seed reconciliation metrics; ground truth is only used here, never by the runtime. */
public class EvaluationService {

    public SeedScorecard evaluate(String seedLabel, EvaluationInput input, List<GroundTruthRecord> groundTruth) {
        List<CanonicalEvent> originators = input.canonicalEvents().stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .toList();
        java.util.Map<String, String> anomalyTypesByInstructionId = new java.util.HashMap<>();
        for (GroundTruthRecord record : groundTruth) {
            anomalyTypesByInstructionId.put(record.instructionId(), record.anomalyType());
        }
        Set<String> exceptionBusinessEventIds = new HashSet<>();
        for (ExceptionRecord exception : input.exceptions()) {
            exceptionBusinessEventIds.add(exception.businessEventId());
        }

        int exactCount = 0;
        int compositeCount = 0;
        int falseMatchCount = 0;
        BigDecimal exactInr = BigDecimal.ZERO;
        BigDecimal compositeInr = BigDecimal.ZERO;
        BigDecimal falseMatchInr = BigDecimal.ZERO;
        BigDecimal matchedInr = BigDecimal.ZERO;
        BigDecimal pendingInr = BigDecimal.ZERO;
        BigDecimal unresolvedInr = BigDecimal.ZERO;
        BigDecimal coveredUnresolvedInr = BigDecimal.ZERO;
        BigDecimal batchTotalInr = BigDecimal.ZERO;
        List<String> falseMatchReferences = new ArrayList<>();
        List<String> unresolvedReferences = new ArrayList<>();

        for (CanonicalEvent event : originators) {
            BigDecimal amount = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
            batchTotalInr = batchTotalInr.add(amount);
            boolean matched = event.getMatchingState() == MatchingState.EXACT_MATCH
                || event.getMatchingState() == MatchingState.COMPOSITE_MATCH;
            boolean pending = event.getMatchingState() == MatchingState.TIMING_DIFFERENCE_PENDING
                || event.getReconciliationState() == ReconciliationState.PENDING;

            if (event.getMatchingState() == MatchingState.EXACT_MATCH) {
                exactCount++;
                exactInr = exactInr.add(amount);
            } else if (event.getMatchingState() == MatchingState.COMPOSITE_MATCH) {
                compositeCount++;
                compositeInr = compositeInr.add(amount);
            }

            if (matched) {
                matchedInr = matchedInr.add(amount);
                String anomalyType = anomalyTypesByInstructionId.get(event.getBusinessEventId());
                if (anomalyType != null && !isExpectedResolution(anomalyType, event.getMatchingState())) {
                    falseMatchCount++;
                    falseMatchInr = falseMatchInr.add(amount);
                    falseMatchReferences.add(event.getBusinessEventId());
                }
            } else if (pending) {
                pendingInr = pendingInr.add(amount);
            } else {
                unresolvedInr = unresolvedInr.add(amount);
                unresolvedReferences.add(event.getBusinessEventId());
                if (exceptionBusinessEventIds.contains(event.getBusinessEventId())) {
                    coveredUnresolvedInr = coveredUnresolvedInr.add(amount);
                }
            }
        }

        int total = originators.size();
        double straightThroughRate = total == 0 ? 0.0 : (double) (exactCount + compositeCount) / total;
        double exceptionCoverageRate = unresolvedInr.signum() == 0
            ? 1.0
            : coveredUnresolvedInr.doubleValue() / unresolvedInr.doubleValue();
        boolean controlTotalIntegrity = matchedInr.add(pendingInr).add(unresolvedInr).compareTo(batchTotalInr) == 0;

        return new SeedScorecard(
            seedLabel, total, exactCount, exactInr, compositeCount, compositeInr,
            falseMatchCount, falseMatchInr, List.copyOf(falseMatchReferences),
            exceptionCoverageRate, straightThroughRate, controlTotalIntegrity,
            batchTotalInr, matchedInr, pendingInr, unresolvedInr, List.copyOf(unresolvedReferences)
        );
    }

    private boolean isExpectedResolution(String anomalyType, MatchingState matchingState) {
        return "COMPOSITE_MATCH".equals(anomalyType) && matchingState == MatchingState.COMPOSITE_MATCH;
    }
}
