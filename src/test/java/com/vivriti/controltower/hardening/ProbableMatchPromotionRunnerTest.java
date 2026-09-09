package com.vivriti.controltower.hardening;

import com.vivriti.controltower.close.CloseHoldDecision;
import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.exceptions.ExceptionStatus;
import com.vivriti.controltower.exceptions.Role;
import com.vivriti.controltower.probabilistic.ProbabilisticMatchConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbableMatchPromotionRunnerTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 1, 18, 0);
    private final Actor requester = new Actor("operator-2", Role.OPERATOR);
    private final Actor approver = new Actor("approver-1", Role.APPROVER);

    @Test
    void confirmReducesUnresolvedInrFlipsHoldToCloseAndSurvivesRestart() throws Exception {
        Path runs = Files.createTempDirectory("promo-confirm-runs");
        Phase1PipelineService pipeline = new Phase1PipelineService(runs, enabled());
        PipelineRunResult run = pipeline.process(batch());
        String fingerprint = run.snapshot().batchFingerprint();
        assertEquals(CloseHoldDecision.Decision.HOLD, run.snapshot().closeHoldDecision().decision());
        assertTrue(run.snapshot().closeHoldDecision().blockingInr().signum() > 0);

        CloseHoldDecision decision = new ProbableMatchPromotionRunner(runs)
            .confirm(fingerprint, "INSTR-001", requester, approver, "Confirmed bank counterpart", CUTOFF);

        assertEquals(CloseHoldDecision.Decision.CLOSE, decision.decision());
        assertEquals(0, decision.blockingInr().signum());

        // Restart: fresh store reads only from disk.
        DurableRunStore reloaded = new DurableRunStore(runs);
        CanonicalEvent originator = originator(reloaded.loadCanonicalRecords(fingerprint));
        assertEquals(MatchingState.PROBABLE_MATCH_CONFIRMED, originator.getMatchingState());
        assertEquals(ReconciliationState.MATCHED, originator.getReconciliationState());
        assertEquals(ExceptionStatus.RESOLVED, latest(exceptionFor(reloaded.loadExceptions(fingerprint))));
        assertTrue(reloaded.loadAudits(fingerprint).stream()
            .anyMatch(entry -> "PROMOTED_PROBABLE_TO_RECONCILED".equals(entry.decision())));
        assertEquals(CloseHoldDecision.Decision.CLOSE, reloaded.loadDecision(fingerprint).decision());
    }

    @Test
    void rejectLeavesStateUnchangedAndAuditsRejection() throws Exception {
        Path runs = Files.createTempDirectory("promo-reject-runs");
        Phase1PipelineService pipeline = new Phase1PipelineService(runs, enabled());
        PipelineRunResult run = pipeline.process(batch());
        String fingerprint = run.snapshot().batchFingerprint();
        assertEquals(CloseHoldDecision.Decision.HOLD, run.snapshot().closeHoldDecision().decision());

        new ProbableMatchPromotionRunner(runs)
            .reject(fingerprint, "INSTR-001", requester, approver, "Insufficient evidence", CUTOFF);

        DurableRunStore reloaded = new DurableRunStore(runs);
        assertEquals(MatchingState.PROBABLE_MATCH, originator(reloaded.loadCanonicalRecords(fingerprint)).getMatchingState());
        assertEquals(ExceptionStatus.OPEN, latest(exceptionFor(reloaded.loadExceptions(fingerprint))));
        assertTrue(reloaded.loadAudits(fingerprint).stream()
            .anyMatch(entry -> "REJECTED_PROBABLE".equals(entry.decision())));
        assertEquals(CloseHoldDecision.Decision.HOLD, reloaded.loadDecision(fingerprint).decision());
    }

    private CanonicalEvent originator(List<CanonicalEvent> canonical) {
        return canonical.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(event -> "INSTR-001".equals(event.getBusinessEventId()))
            .findFirst().orElseThrow();
    }

    private ExceptionRecord exceptionFor(List<ExceptionRecord> exceptions) {
        return exceptions.stream().filter(exception -> "INSTR-001".equals(exception.businessEventId()))
            .findFirst().orElseThrow();
    }

    private ExceptionStatus latest(ExceptionRecord exception) {
        return exception.statusHistory().get(exception.statusHistory().size() - 1).status();
    }

    private PipelineBatch batch() {
        return new PipelineBatch("BATCH-001",
            List.of("INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,50000.00,INR,APPROVED,BATCH-001,2026-08-01T17:05:00"),
            List.of("BOOK-001,LOAN-INT-001,LOAN-001,2026-08-01T17:10:00,50050.00,INR,APPROVED,BATCH-001"),
            List.of("TXN-001,INSTR-001,2026-08-01T17:20:00,50000.00,POSTED,,BATCH-001"),
            CUTOFF, List.of());
    }

    private ProbabilisticMatchConfig enabled() {
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80);
    }
}
