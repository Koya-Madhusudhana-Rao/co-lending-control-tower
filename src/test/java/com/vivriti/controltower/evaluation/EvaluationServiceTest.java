package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.CauseConfidence;
import com.vivriti.controltower.exceptions.ExceptionClassification;
import com.vivriti.controltower.exceptions.ExceptionDetection;
import com.vivriti.controltower.exceptions.ExceptionQueueService;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.exceptions.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationServiceTest {

    private final EvaluationService service = new EvaluationService();

    @Test
    void computesExactMatchCountAndInrFromHandFixture() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED),
            originator("INSTR-2", "250.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertEquals(2, scorecard.exactMatchCount());
        assertTrue(scorecard.exactMatchInr().compareTo(new BigDecimal("350.00")) == 0);
    }

    @Test
    void computesCompositeMatchCountAndInrFromHandFixture() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "60.00", MatchingState.COMPOSITE_MATCH, ReconciliationState.MATCHED),
            originator("INSTR-2", "40.00", MatchingState.COMPOSITE_MATCH, ReconciliationState.MATCHED)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertEquals(2, scorecard.compositeMatchCount());
        assertTrue(scorecard.compositeMatchInr().compareTo(new BigDecimal("100.00")) == 0);
    }

    @Test
    void reportsFalseMatchExposureWhenMatchedRecordIsAGroundTruthAnomaly() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED)
        ), List.of());
        var groundTruth = List.of(new GroundTruthRecord("INSTR-1", new BigDecimal("100.00"), "INR", "AMOUNT_MISMATCH", "APPROVED"));

        SeedScorecard scorecard = service.evaluate("SEED-A", input, groundTruth);

        assertEquals(1, scorecard.falseMatchCount());
        assertTrue(scorecard.falseMatchInr().compareTo(new BigDecimal("100.00")) == 0);
        assertEquals(List.of("INSTR-1"), scorecard.falseMatchReferences());
    }

    @Test
    void doesNotCountExpectedCompositeResolutionAsFalseMatchExposure() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.COMPOSITE_MATCH, ReconciliationState.MATCHED)
        ), List.of());
        var groundTruth = List.of(new GroundTruthRecord("INSTR-1", new BigDecimal("100.00"), "INR", "COMPOSITE_MATCH", "APPROVED"));

        SeedScorecard scorecard = service.evaluate("SEED-A", input, groundTruth);

        assertEquals(0, scorecard.falseMatchCount());
        assertTrue(scorecard.falseMatchInr().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void doesNotCountCorrectlyResolvedDuplicateAsFalseMatchExposure() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED)
        ), List.of());
        var groundTruth = List.of(new GroundTruthRecord("INSTR-1", new BigDecimal("100.00"), "INR", "DUPLICATE_EVENT", "APPROVED"));

        SeedScorecard scorecard = service.evaluate("SEED-A", input, groundTruth);

        assertEquals(0, scorecard.falseMatchCount());
        assertTrue(scorecard.falseMatchInr().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void reportsZeroFalseMatchExposureWhenNoMatchedRecordIsAnomalous() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertEquals(0, scorecard.falseMatchCount());
        assertTrue(scorecard.falseMatchInr().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void computesExceptionCoverageAsShareOfUnresolvedInrRepresentedInQueue() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "300.00", null, null),
            originator("INSTR-2", "100.00", null, null)
        ), List.of(exceptionFor("INSTR-1")));

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertTrue(scorecard.unresolvedInr().compareTo(new BigDecimal("400.00")) == 0);
        assertEquals(0.75, scorecard.exceptionCoverageRate(), 0.0001);
    }

    @Test
    void computesStraightThroughRateFromMatchedShareOfBusinessEvents() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED),
            originator("INSTR-2", "100.00", MatchingState.COMPOSITE_MATCH, ReconciliationState.MATCHED),
            originator("INSTR-3", "100.00", null, null),
            originator("INSTR-4", "100.00", null, null)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertEquals(0.5, scorecard.straightThroughRate(), 0.0001);
    }

    @Test
    void controlTotalIntegrityHoldsWhenMatchedPendingUnresolvedSumToBatchTotal() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.EXACT_MATCH, ReconciliationState.MATCHED),
            originator("INSTR-2", "50.00", MatchingState.TIMING_DIFFERENCE_PENDING, ReconciliationState.PENDING),
            originator("INSTR-3", "25.00", null, null)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertTrue(scorecard.controlTotalIntegrity());
        assertTrue(scorecard.batchTotalInr().compareTo(new BigDecimal("175.00")) == 0);
        assertTrue(scorecard.matchedInr().compareTo(new BigDecimal("100.00")) == 0);
        assertTrue(scorecard.pendingInr().compareTo(new BigDecimal("50.00")) == 0);
        assertTrue(scorecard.unresolvedInr().compareTo(new BigDecimal("25.00")) == 0);
    }

    @Test
    void pendingTimingItemsAreNotCountedAsUnresolved() {
        var input = new EvaluationInput(List.of(
            originator("INSTR-1", "100.00", MatchingState.TIMING_DIFFERENCE_PENDING, ReconciliationState.PENDING)
        ), List.of());

        SeedScorecard scorecard = service.evaluate("SEED-A", input, List.of());

        assertTrue(scorecard.unresolvedInr().compareTo(BigDecimal.ZERO) == 0);
        assertFalse(scorecard.unresolvedReferences().contains("INSTR-1"));
    }

    private CanonicalEvent originator(String id, String amount, MatchingState matching, ReconciliationState reconciliation) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(SourceSystem.ORIGINATOR);
        event.setBusinessEventId(id);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency("INR");
        event.setMatchingState(matching);
        event.setReconciliationState(reconciliation);
        return event;
    }

    private ExceptionRecord exceptionFor(String businessEventId) {
        CanonicalEvent event = new CanonicalEvent();
        event.setBusinessEventId(businessEventId);
        event.setSourcePartner("PARA");
        event.setAmount(new BigDecimal("300.00"));
        return new ExceptionQueueService().create(new ExceptionDetection(
            event, ExceptionClassification.MISSING_EVENT, List.of("source.csv#line=1"),
            "rule", "evidence", CauseConfidence.INFERRED, "cause",
            LocalDateTime.of(2026, 8, 2, 10, 0), new Actor("operator-1", Role.OPERATOR)));
    }
}
