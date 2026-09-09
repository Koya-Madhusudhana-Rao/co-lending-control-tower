package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionMaterializerTest {

    private final ExceptionMaterializer materializer = new ExceptionMaterializer();
    private final LocalDateTime detectedAt = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void materializesMissingEventExceptionWhenBankCounterpartAbsent() {
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", "100.00", "APPROVED");
        CanonicalEvent lms = lms("LOAN-1", "100.00", "APPROVED");

        List<ExceptionRecord> records = materializer.materialize(List.of(originator, lms), List.of(), detectedAt);

        assertEquals(1, records.size());
        assertEquals(ExceptionClassification.MISSING_EVENT, records.get(0).classification());
        assertEquals("INSTR-1", records.get(0).businessEventId());
    }

    @Test
    void materializesAmountMismatchExceptionWhenSourcesDisagreeOnValue() {
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", "100.00", "APPROVED");
        CanonicalEvent lms = lms("LOAN-1", "150.00", "APPROVED");
        CanonicalEvent bank = bank("INSTR-1", "100.00", "POSTED");

        List<ExceptionRecord> records = materializer.materialize(List.of(originator, lms, bank), List.of(), detectedAt);

        assertEquals(ExceptionClassification.AMOUNT_MISMATCH, records.get(0).classification());
    }

    @Test
    void materializesStatusMismatchExceptionWhenStatusesDisagree() {
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", "100.00", "APPROVED");
        CanonicalEvent lms = lms("LOAN-1", "100.00", "BOOKING_REVIEW");
        CanonicalEvent bank = bank("INSTR-1", "100.00", "POSTED");

        List<ExceptionRecord> records = materializer.materialize(List.of(originator, lms, bank), List.of(), detectedAt);

        assertEquals(ExceptionClassification.STATUS_MISMATCH, records.get(0).classification());
    }

    @Test
    void materializesDuplicateExceptionRoutedToEngineeringSourcePartner() {
        CanonicalEvent survivor = originator("INSTR-1", "LOAN-1", "100.00", "APPROVED");
        survivor.setMatchingState(MatchingState.EXACT_MATCH);
        survivor.setReconciliationState(ReconciliationState.MATCHED);

        List<ExceptionRecord> records = materializer.materialize(List.of(survivor), List.of("INSTR-1"), detectedAt);

        assertEquals(1, records.size());
        assertEquals(ExceptionClassification.DUPLICATE_EVENT, records.get(0).classification());
        assertEquals("Engineering / Source Partner", records.get(0).owner());
    }

    @Test
    void skipsMatchedAndPendingEventsWhenMaterializing() {
        CanonicalEvent matched = originator("INSTR-1", "LOAN-1", "100.00", "APPROVED");
        matched.setMatchingState(MatchingState.EXACT_MATCH);
        matched.setReconciliationState(ReconciliationState.MATCHED);
        CanonicalEvent pending = originator("INSTR-2", "LOAN-2", "100.00", "APPROVED");
        pending.setMatchingState(MatchingState.TIMING_DIFFERENCE_PENDING);
        pending.setReconciliationState(ReconciliationState.PENDING);

        List<ExceptionRecord> records = materializer.materialize(List.of(matched, pending), List.of(), detectedAt);

        assertTrue(records.isEmpty());
    }

    private CanonicalEvent originator(String instructionId, String loanReference, String amount, String status) {
        CanonicalEvent event = base(SourceSystem.ORIGINATOR, amount, status);
        event.setBusinessEventId(instructionId);
        event.setPartnerLoanReference(loanReference);
        event.setSourcePartner("PARA");
        return event;
    }

    private CanonicalEvent lms(String loanReference, String amount, String status) {
        CanonicalEvent event = base(SourceSystem.LMS, amount, status);
        event.setPartnerLoanReference(loanReference);
        return event;
    }

    private CanonicalEvent bank(String correlationId, String amount, String status) {
        CanonicalEvent event = base(SourceSystem.BANK, amount, status);
        event.setCorrelationId(correlationId);
        return event;
    }

    private CanonicalEvent base(SourceSystem source, String amount, String status) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(source);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency("INR");
        event.setSourceStatus(status);
        event.setValidationState(ValidationState.VALID);
        return event;
    }
}
