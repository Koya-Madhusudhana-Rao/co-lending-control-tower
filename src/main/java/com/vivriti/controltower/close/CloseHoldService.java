package com.vivriti.controltower.close;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.exceptions.ExceptionStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CloseHoldService {
    private final CloseHoldPolicy policy;

    public CloseHoldService(CloseHoldPolicy policy) {
        this.policy = policy;
    }

    public CloseHoldDecision decide(
        BigDecimal batchTotalInr,
        List<CanonicalEvent> canonicalEvents,
        List<ExceptionRecord> exceptions
    ) {
        BigDecimal threshold = policy.thresholdFor(batchTotalInr);
        BigDecimal blockingAmount = BigDecimal.ZERO;
        List<String> references = new ArrayList<>();
        Set<String> countedKeys = new HashSet<>();

        for (CanonicalEvent event : canonicalEvents) {
            if (!isBlocking(event)) {
                continue;
            }
            String key = stableKey(event.getBusinessEventId(), event.getRawSourceLocation(), event.getImmutableSourceRecordId());
            if (countedKeys.add(key)) {
                blockingAmount = blockingAmount.add(amount(event.getAmount()));
                references.add(reference(event.getRawSourceLocation(), event.getImmutableSourceRecordId(), event.getBusinessEventId()));
            }
        }

        for (ExceptionRecord exception : exceptions) {
            if (latestStatus(exception) != ExceptionStatus.OPEN) {
                continue;
            }
            String key = stableKey(exception.businessEventId(), first(exception.affectedSourceRecordReferences()), exception.exceptionId());
            if (countedKeys.add(key)) {
                blockingAmount = blockingAmount.add(amount(exception.amountInr()));
                references.add(first(exception.affectedSourceRecordReferences()) == null
                    ? exception.exceptionId() : first(exception.affectedSourceRecordReferences()));
            }
        }

        return new CloseHoldDecision(
            blockingAmount.compareTo(threshold) > 0 ? CloseHoldDecision.Decision.HOLD : CloseHoldDecision.Decision.CLOSE,
            threshold,
            blockingAmount,
            references.stream().sorted().toList()
        );
    }

    private boolean isBlocking(CanonicalEvent event) {
        if (event.getMatchingState() == MatchingState.TIMING_DIFFERENCE_PENDING
            || event.getReconciliationState() == ReconciliationState.PENDING) {
            return false;
        }
        return (event.getMatchingState() == null || event.getMatchingState() == MatchingState.UNMATCHED)
            && event.getReconciliationState() == null;
    }

    private ExceptionStatus latestStatus(ExceptionRecord exception) {
        return exception.statusHistory().isEmpty()
            ? ExceptionStatus.OPEN
            : exception.statusHistory().get(exception.statusHistory().size() - 1).status();
    }

    private BigDecimal amount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String stableKey(String businessEventId, String sourceReference, String fallback) {
        return businessEventId != null ? "BUSINESS:" + businessEventId
            : sourceReference != null ? "SOURCE:" + sourceReference : "FALLBACK:" + fallback;
    }

    private String reference(String sourceReference, String immutableId, String businessEventId) {
        return sourceReference != null ? sourceReference : immutableId != null ? immutableId : businessEventId;
    }

    private String first(List<String> references) {
        return references.isEmpty() ? null : references.get(0);
    }
}