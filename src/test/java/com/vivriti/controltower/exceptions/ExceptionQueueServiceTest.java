package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ValidationState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionQueueServiceTest {

    private final ExceptionQueueService service = new ExceptionQueueService();
    private final LocalDateTime detectedAt = LocalDateTime.of(2026, 8, 2, 10, 0);

    @Test
    void createsOneExceptionPerMandatoryClassificationWithDefaultOwnership() {
        assertEquals("Partner Ops / Integration Support", create(ExceptionClassification.MISSING_EVENT).owner());
        assertEquals("Engineering / Source Partner", create(ExceptionClassification.DUPLICATE_EVENT).owner());
        assertEquals("Finance / Lending Ops", create(ExceptionClassification.AMOUNT_MISMATCH).owner());
        assertEquals("Operations", create(ExceptionClassification.STATUS_MISMATCH).owner());
        assertEquals("Partner Ops / Integration Support", create(ExceptionClassification.TIMING_DIFFERENCE).owner());
        assertEquals("Finance / Lending Ops", create(ExceptionClassification.COMPOSITE_MATCH).owner());
    }

    @Test
    void exceptionCreationDoesNotMutateCanonicalSourceRecord() {
        CanonicalEvent source = event("BUS-001");
        String beforeId = source.getBusinessEventId();
        ValidationState beforeValidation = source.getValidationState();

        ExceptionRecord exception = service.create(detection(source, ExceptionClassification.AMOUNT_MISMATCH));

        assertNotNull(exception);
        assertEquals(beforeId, source.getBusinessEventId());
        assertEquals(beforeValidation, source.getValidationState());
        assertEquals(null, source.getMatchingState());
        assertEquals(null, source.getReconciliationState());
    }

    @Test
    void sameDetectionInputProducesSameDeterministicExceptionId() {
        CanonicalEvent source = event("BUS-001");
        ExceptionRecord first = service.create(detection(source, ExceptionClassification.DUPLICATE_EVENT));
        ExceptionRecord second = service.create(detection(source, ExceptionClassification.DUPLICATE_EVENT));

        assertEquals(first.exceptionId(), second.exceptionId());
    }

    @Test
    void exceptionCarriesEvidenceHistoryAndEmptyOverrideHistory() {
        ExceptionRecord exception = create(ExceptionClassification.TIMING_DIFFERENCE);

        assertEquals(1, exception.statusHistory().size());
        assertEquals(ExceptionStatus.OPEN, exception.statusHistory().get(0).status());
        assertEquals(0, exception.overrideHistory().size());
        assertEquals(CauseConfidence.INFERRED, exception.causeConfidence());
        assertEquals("rule-TIMING_DIFFERENCE", exception.rule());
        assertEquals("raw evidence", exception.evidence());
    }

    @Test
    void rejectsEventsAlreadyMatchedOrReconciled() {
        CanonicalEvent source = event("BUS-001");
        source.setMatchingState(com.vivriti.controltower.domain.MatchingState.EXACT_MATCH);

        assertThrows(IllegalArgumentException.class, () -> service.create(detection(source, ExceptionClassification.AMOUNT_MISMATCH)));
    }

    private ExceptionRecord create(ExceptionClassification classification) {
        return service.create(detection(event("BUS-" + classification), classification));
    }

    private ExceptionDetection detection(CanonicalEvent source, ExceptionClassification classification) {
        return new ExceptionDetection(source, classification, List.of("source.csv#line=1"),
            "rule-" + classification, "raw evidence", CauseConfidence.INFERRED,
            "Likely source feed issue", detectedAt);
    }

    private CanonicalEvent event(String id) {
        CanonicalEvent event = new CanonicalEvent();
        event.setBusinessEventId(id);
        event.setSourcePartner("PARA");
        event.setAmount(new BigDecimal("100.00"));
        event.setSourceTimestamp(detectedAt.minusHours(3));
        event.setIngestionState(IngestionState.VALIDATED);
        event.setValidationState(ValidationState.VALID);
        return event;
    }
}