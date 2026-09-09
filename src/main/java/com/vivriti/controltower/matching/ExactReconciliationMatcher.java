package com.vivriti.controltower.matching;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ExactReconciliationMatcher {

    private final BigDecimal amountToleranceInr;

    public ExactReconciliationMatcher(BigDecimal amountToleranceInr) {
        this.amountToleranceInr = amountToleranceInr;
    }

    public ExactMatchResult reconcile(List<CanonicalEvent> events) {
        List<CanonicalEvent> matchedEvents = new ArrayList<>();
        int matchedGroups = 0;

        for (CanonicalEvent originator : eventsOf(events, SourceSystem.ORIGINATOR)) {
            List<CanonicalEvent> lmsCandidates = events.stream()
                .filter(event -> event.getSourceSystem() == SourceSystem.LMS)
                .filter(event -> originator.getPartnerLoanReference() != null)
                .filter(event -> originator.getPartnerLoanReference().equals(event.getPartnerLoanReference()))
                .toList();
            List<CanonicalEvent> bankCandidates = events.stream()
                .filter(event -> event.getSourceSystem() == SourceSystem.BANK)
                .filter(event -> originator.getBusinessEventId() != null)
                .filter(event -> originator.getBusinessEventId().equals(event.getCorrelationId()))
                .toList();

            if (lmsCandidates.size() != 1 || bankCandidates.size() != 1) {
                continue;
            }

            CanonicalEvent lms = lmsCandidates.get(0);
            CanonicalEvent bank = bankCandidates.get(0);
            if (!validForExact(originator, lms, bank)
                || !compatible(originator, lms) || !compatible(originator, bank)
                || !compatible(lms, bank) || !compatibleEventTypes(originator, lms, bank)
                || !compatibleStatuses(originator, lms, bank)
                || !expectedRelationshipsAgree(originator, lms, bank)) {
                continue;
            }

            for (CanonicalEvent event : List.of(originator, lms, bank)) {
                event.setMatchingState(MatchingState.EXACT_MATCH);
                event.setReconciliationState(ReconciliationState.MATCHED);
                matchedEvents.add(event);
            }
            matchedGroups++;
        }

        return new ExactMatchResult(matchedGroups, List.copyOf(matchedEvents));
    }

    private boolean compatible(CanonicalEvent first, CanonicalEvent second) {
        return first.getCurrency() != null
            && first.getCurrency().equals(second.getCurrency())
            && first.getAmount() != null
            && second.getAmount() != null
            && first.getAmount().subtract(second.getAmount()).abs().compareTo(amountToleranceInr) <= 0;
    }

    private boolean validForExact(CanonicalEvent originator, CanonicalEvent lms, CanonicalEvent bank) {
        return originator.getValidationState() == ValidationState.VALID
            && lms.getValidationState() == ValidationState.VALID
            && bank.getValidationState() == ValidationState.VALID;
    }

    private boolean expectedRelationshipsAgree(CanonicalEvent originator, CanonicalEvent lms, CanonicalEvent bank) {
        return originator.getBusinessEventId() != null
            && originator.getPartnerLoanReference() != null
            && originator.getBusinessEventId().equals(bank.getCorrelationId())
            && originator.getPartnerLoanReference().equals(lms.getPartnerLoanReference())
            && lms.getPartnerLoanReference().equals(lms.getCorrelationId())
            && bank.getCorrelationId().equals(bank.getParentReference());
    }

    private boolean compatibleEventTypes(CanonicalEvent originator, CanonicalEvent lms, CanonicalEvent bank) {
        return "DISBURSEMENT_INSTRUCTION".equals(originator.getEventType())
            && "LOAN_BOOKING".equals(lms.getEventType())
            && ("SETTLEMENT".equals(bank.getEventType()) || "SETTLEMENT_REVERSAL".equals(bank.getEventType()));
    }

    private boolean compatibleStatuses(CanonicalEvent originator, CanonicalEvent lms, CanonicalEvent bank) {
        return originator.getSourceStatus() != null
            && originator.getSourceStatus().equals(lms.getSourceStatus())
            && "POSTED".equals(bank.getSourceStatus());
    }

    private List<CanonicalEvent> eventsOf(List<CanonicalEvent> events, SourceSystem sourceSystem) {
        return events.stream().filter(event -> event.getSourceSystem() == sourceSystem).toList();
    }
}