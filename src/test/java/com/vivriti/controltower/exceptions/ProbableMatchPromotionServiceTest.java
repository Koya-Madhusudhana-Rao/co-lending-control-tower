package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.FieldScores;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ThresholdBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbableMatchPromotionServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 1, 18, 0);
    private final Actor creator = new Actor("operator-1", Role.OPERATOR);
    private final Actor requester = new Actor("operator-2", Role.OPERATOR);
    private final Actor approver = new Actor("approver-1", Role.APPROVER);

    @Test
    void rejectsSelfApprovalByCreator() {
        AppendOnlyAuditTrail trail = new AppendOnlyAuditTrail();
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(trail);

        assertThrows(IllegalArgumentException.class, () -> service.confirm(
            exception(creator), probable(ThresholdBand.CONFIRMATION_ELIGIBLE),
            requester, new Actor("operator-1", Role.APPROVER), "looks right", T));
    }

    @Test
    void rejectsRequesterApprovingOwnProposal() {
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(new AppendOnlyAuditTrail());

        assertThrows(IllegalArgumentException.class, () -> service.confirm(
            exception(creator), probable(ThresholdBand.CONFIRMATION_ELIGIBLE),
            new Actor("dup", Role.OPERATOR), new Actor("dup", Role.APPROVER), "looks right", T));
    }

    @Test
    void rejectsBlankReason() {
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(new AppendOnlyAuditTrail());

        assertThrows(IllegalArgumentException.class, () -> service.confirm(
            exception(creator), probable(ThresholdBand.CONFIRMATION_ELIGIBLE), requester, approver, "  ", T));
    }

    @Test
    void rejectsIneligibleLowerBand() {
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(new AppendOnlyAuditTrail());

        assertThrows(IllegalArgumentException.class, () -> service.confirm(
            exception(creator), probable(ThresholdBand.PROBABLE_UNRESOLVED), requester, approver, "looks right", T));
    }

    @Test
    void confirmPromotesStateResolvesExceptionAndWritesAudit() {
        AppendOnlyAuditTrail trail = new AppendOnlyAuditTrail();
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(trail);
        CanonicalEvent probable = probable(ThresholdBand.CONFIRMATION_ELIGIBLE);

        ProbablePromotionResult result = service.confirm(
            exception(creator), probable, requester, approver, "Finance confirmed the counterpart", T);

        assertEquals(MatchingState.PROBABLE_MATCH_CONFIRMED, probable.getMatchingState());
        assertEquals(ReconciliationState.MATCHED, probable.getReconciliationState());
        assertEquals(ExceptionStatus.RESOLVED, latest(result.exception()));
        assertEquals(1, result.exception().overrideHistory().size());
        assertTrue(result.exception().overrideHistory().get(0).contains("decision=PROMOTED_PROBABLE_TO_RECONCILED"));
        assertEquals(1, trail.entries().size());
        assertEquals("PROMOTED_PROBABLE_TO_RECONCILED", trail.entries().get(0).decision());
        assertTrue(trail.entries().get(0).rule().contains("probabilistic-match-v1"));
        assertTrue(trail.entries().get(0).rule().contains("score="));
    }

    @Test
    void rejectAuditsWithoutChangingState() {
        AppendOnlyAuditTrail trail = new AppendOnlyAuditTrail();
        ProbableMatchPromotionService service = new ProbableMatchPromotionService(trail);
        CanonicalEvent probable = probable(ThresholdBand.CONFIRMATION_ELIGIBLE);
        ExceptionRecord exception = exception(creator);

        ProbablePromotionResult result = service.reject(exception, probable, requester, approver, "insufficient evidence", T);

        assertEquals("REJECTED_PROBABLE", trail.entries().get(0).decision());
        assertEquals(MatchingState.PROBABLE_MATCH, probable.getMatchingState());
        assertEquals(ExceptionStatus.OPEN, latest(result.exception()));
        assertEquals(0, result.exception().overrideHistory().size());
    }

    private ExceptionStatus latest(ExceptionRecord exception) {
        return exception.statusHistory().get(exception.statusHistory().size() - 1).status();
    }

    private ExceptionRecord exception(Actor creator) {
        CanonicalEvent event = new CanonicalEvent();
        event.setBusinessEventId("BUS-1");
        event.setSourcePartner("PARA");
        event.setAmount(new BigDecimal("5000.00"));
        event.setSourceTimestamp(T);
        return new ExceptionQueueService().create(new ExceptionDetection(
            event, ExceptionClassification.AMOUNT_MISMATCH, List.of("originator.csv#line=1"),
            "amount-rule", "evidence", CauseConfidence.INFERRED, "cause", T, creator));
    }

    private CanonicalEvent probable(ThresholdBand band) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(SourceSystem.ORIGINATOR);
        event.setBusinessEventId("BUS-1");
        event.setImmutableSourceRecordId("INSTR-1");
        event.setAmount(new BigDecimal("5000.00"));
        event.setMatchingState(MatchingState.PROBABLE_MATCH);
        event.setProbableMatchEvidence(new ProbableMatchEvidence(
            0.85, new FieldScores(1.0, 0.9, 0.9, 0.0), "BOOK-1", band, 0.50, 0.80));
        return event;
    }
}
