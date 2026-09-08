package com.vivriti.controltower.close;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.AppendOnlyAuditTrail;
import com.vivriti.controltower.exceptions.CauseConfidence;
import com.vivriti.controltower.exceptions.ExceptionClassification;
import com.vivriti.controltower.exceptions.ExceptionDetection;
import com.vivriti.controltower.exceptions.ExceptionOverrideService;
import com.vivriti.controltower.exceptions.ExceptionQueueService;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.exceptions.ExceptionStatus;
import com.vivriti.controltower.exceptions.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseHoldServiceTest {

    private final CloseHoldService service = new CloseHoldService(
        CloseHoldPolicy.fromConfig(Path.of("config", "reconciliation.yml")));
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);

    @Test
    void closesWhenOnlyInGraceTimingPendingItemsRemain() {
        CanonicalEvent pending = event("PENDING-1", "100.00");
        pending.setMatchingState(MatchingState.TIMING_DIFFERENCE_PENDING);
        pending.setReconciliationState(ReconciliationState.PENDING);

        CloseHoldDecision decision = service.decide(new BigDecimal("1000000.00"), List.of(pending), List.of());

        assertEquals(CloseHoldDecision.Decision.CLOSE, decision.decision());
        assertEquals(BigDecimal.ZERO, decision.blockingInr());
    }

    @Test
    void holdsWhenUnresolvedAmountExceedsAbsoluteThreshold() {
        CanonicalEvent unresolved = event("UNRESOLVED-1", "10001.00");

        CloseHoldDecision decision = service.decide(new BigDecimal("3000000.00"), List.of(unresolved), List.of());

        assertEquals(CloseHoldDecision.Decision.HOLD, decision.decision());
        assertEquals(new BigDecimal("10001.00"), decision.blockingInr());
        assertEquals(List.of("UNRESOLVED-1"), decision.blockingRecordReferences());
    }

    @Test
    void holdsWhenUnresolvedAmountExceedsRelativeThresholdOnSmallBatch() {
        CanonicalEvent unresolved = event("UNRESOLVED-2", "5001.00");

        CloseHoldDecision decision = service.decide(new BigDecimal("1000000.00"), List.of(unresolved), List.of());

        assertEquals(CloseHoldDecision.Decision.HOLD, decision.decision());
        assertTrue(decision.thresholdInr().compareTo(new BigDecimal("5000.00")) == 0);
    }

    @Test
    void approvedOverrideRemovesExceptionFromBlockingSet() {
        ExceptionRecord exception = new ExceptionQueueService().create(new ExceptionDetection(
            event("EXCEPTION-1", "11000.00"), ExceptionClassification.AMOUNT_MISMATCH,
            List.of("source.csv#line=1"), "amount-rule", "amount evidence", CauseConfidence.CONFIRMED,
            "Source amount differs", now, new Actor("operator-1", Role.OPERATOR)));
        AppendOnlyAuditTrail audit = new AppendOnlyAuditTrail();
        ExceptionRecord resolved = new ExceptionOverrideService(audit).approveStatusOverride(
            exception, new Actor("operator-2", Role.OPERATOR), new Actor("approver-1", Role.APPROVER),
            ExceptionStatus.RESOLVED, "Finance approved correction", now).exception();

        CloseHoldDecision before = service.decide(new BigDecimal("3000000.00"), List.of(), List.of(exception));
        CloseHoldDecision after = service.decide(new BigDecimal("3000000.00"), List.of(), List.of(resolved));

        assertEquals(CloseHoldDecision.Decision.HOLD, before.decision());
        assertEquals(CloseHoldDecision.Decision.CLOSE, after.decision());
    }

    @Test
    void sameBatchStateProducesSameDecisionAndBlockingReferences() {
        CanonicalEvent first = event("B-2", "6000.00");
        CanonicalEvent second = event("B-1", "6000.00");

        CloseHoldDecision firstDecision = service.decide(new BigDecimal("3000000.00"), List.of(first, second), List.of());
        CloseHoldDecision secondDecision = service.decide(new BigDecimal("3000000.00"), List.of(first, second), List.of());

        assertEquals(firstDecision, secondDecision);
        assertEquals(List.of("B-1", "B-2"), firstDecision.blockingRecordReferences());
    }

    private CanonicalEvent event(String id, String amount) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(SourceSystem.ORIGINATOR);
        event.setBusinessEventId(id);
        event.setImmutableSourceRecordId(id);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency("INR");
        event.setIngestionState(IngestionState.VALIDATED);
        event.setValidationState(ValidationState.VALID);
        return event;
    }
}