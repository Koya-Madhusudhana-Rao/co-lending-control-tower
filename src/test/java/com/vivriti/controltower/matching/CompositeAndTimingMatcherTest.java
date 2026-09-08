package com.vivriti.controltower.matching;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompositeAndTimingMatcherTest {

    private final CompositeAndTimingMatcher matcher = new CompositeAndTimingMatcher();
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 8, 1, 17, 30);

    @Test
    void matchesOneOriginatorToManyLmsBookingsWhenSumsBalanceExactly() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "100.00");
        CanonicalEvent firstBooking = event(SourceSystem.LMS, "BOOK-001", "LOAN-001", "60.00");
        CanonicalEvent secondBooking = event(SourceSystem.LMS, "BOOK-002", "LOAN-001", "40.00");

        CompositeAndTimingResult result = matcher.reconcile(List.of(originator, firstBooking, secondBooking));

        assertEquals(1, result.compositeGroups());
        assertEquals(3, result.compositeEvents().size());
        assertEquals(MatchingState.COMPOSITE_MATCH, originator.getMatchingState());
        assertEquals(ReconciliationState.MATCHED, secondBooking.getReconciliationState());
    }

    @Test
    void matchesManyOriginatorsToOneLmsBookingWhenSumsBalanceExactly() {
        CanonicalEvent firstInstruction = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "60.00");
        CanonicalEvent secondInstruction = event(SourceSystem.ORIGINATOR, "INSTR-002", "LOAN-001", "40.00");
        CanonicalEvent booking = event(SourceSystem.LMS, "BOOK-001", "LOAN-001", "100.00");

        CompositeAndTimingResult result = matcher.reconcile(List.of(firstInstruction, secondInstruction, booking));

        assertEquals(1, result.compositeGroups());
        assertEquals(3, result.compositeEvents().size());
        assertEquals(MatchingState.COMPOSITE_MATCH, firstInstruction.getMatchingState());
        assertEquals(ReconciliationState.MATCHED, booking.getReconciliationState());
    }

    @Test
    void doesNotCompositeMatchWhenSumDiffersByOneCent() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "100.00");
        CanonicalEvent firstBooking = event(SourceSystem.LMS, "BOOK-001", "LOAN-001", "60.00");
        CanonicalEvent secondBooking = event(SourceSystem.LMS, "BOOK-002", "LOAN-001", "39.99");

        CompositeAndTimingResult result = matcher.reconcile(List.of(originator, firstBooking, secondBooking));

        assertEquals(0, result.compositeGroups());
        assertNull(originator.getMatchingState());
        assertNull(firstBooking.getReconciliationState());
    }

    @Test
    void marksLateRecordInsideGraceWindowAsPendingTimingDifference() {
        CanonicalEvent late = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "100.00");
        late.setValidationState(ValidationState.LATE_ARRIVAL);
        late.setReconciliationCutOff(cutoff);
        late.setReceivedTimestamp(cutoff.plusHours(2).minusMinutes(1));

        CompositeAndTimingResult result = matcher.reconcile(List.of(late));

        assertEquals(1, result.timingEvents().size());
        assertEquals(MatchingState.TIMING_DIFFERENCE_PENDING, late.getMatchingState());
        assertEquals(ReconciliationState.PENDING, late.getReconciliationState());
    }

    @Test
    void leavesLateRecordOutsideGraceWindowUnsetForExceptionStage() {
        CanonicalEvent late = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "100.00");
        late.setValidationState(ValidationState.LATE_ARRIVAL);
        late.setReconciliationCutOff(cutoff);
        late.setReceivedTimestamp(cutoff.plusHours(2).plusMinutes(1));

        CompositeAndTimingResult result = matcher.reconcile(List.of(late));

        assertEquals(0, result.timingEvents().size());
        assertNull(late.getMatchingState());
        assertNull(late.getReconciliationState());
    }

    @Test
    void excludesLevelOneMatchedRecordFromCompositeAndTimingPool() {
        CanonicalEvent exactOriginator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "100.00");
        exactOriginator.setMatchingState(MatchingState.EXACT_MATCH);
        exactOriginator.setReconciliationState(ReconciliationState.MATCHED);
        CanonicalEvent firstBooking = event(SourceSystem.LMS, "BOOK-001", "LOAN-001", "60.00");
        CanonicalEvent secondBooking = event(SourceSystem.LMS, "BOOK-002", "LOAN-001", "40.00");

        CompositeAndTimingResult result = matcher.reconcile(List.of(exactOriginator, firstBooking, secondBooking));

        assertEquals(0, result.compositeGroups());
        assertEquals(MatchingState.EXACT_MATCH, exactOriginator.getMatchingState());
        assertNull(firstBooking.getMatchingState());
        assertNull(secondBooking.getReconciliationState());
    }

    private CanonicalEvent event(SourceSystem source, String id, String relationship, String amount) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(source);
        event.setImmutableSourceRecordId(id);
        event.setBusinessEventId(id);
        event.setPartnerLoanReference(relationship);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency("INR");
        event.setEventType(source == SourceSystem.ORIGINATOR ? "DISBURSEMENT_INSTRUCTION" : "LOAN_BOOKING");
        event.setIngestionState(IngestionState.VALIDATED);
        event.setValidationState(ValidationState.VALID);
        return event;
    }
}