package com.vivriti.controltower.hardening;

import com.vivriti.controltower.exceptions.CauseConfidence;
import com.vivriti.controltower.exceptions.ExceptionClassification;
import com.vivriti.controltower.exceptions.ExceptionDetection;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.Role;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase1PipelineServiceTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 1, 17, 30);

    @Test
    void exactDuplicateBatchRerunProducesIdenticalStateWithoutDuplicateExceptionsOrAudits() {
        Phase1PipelineService pipeline = new Phase1PipelineService();
        PipelineBatch batch = batch("BATCH-001", validOriginator(), validLms(), validBank(), List.of(exceptionDetection()));

        PipelineSnapshot first = pipeline.process(batch).snapshot();
        PipelineSnapshot second = pipeline.process(batch).snapshot();

        assertEquals(first, second);
        assertEquals(3, first.canonicalRecordFingerprints().size());
        assertEquals(first.exceptionIds(), second.exceptionIds());
        assertEquals(1, first.exceptionIds().size());
        assertEquals(0, second.auditEntryCount());
    }

    @Test
    void corruptBatchDoesNotAffectSeparateValidBatchInSameRun() {
        Phase1PipelineService pipeline = new Phase1PipelineService();
        PipelineRunResult corrupt = pipeline.process(new PipelineBatch(
            "BATCH-001", List.of("not,a,valid,row"), validLms(), validBank(), CUTOFF, List.of()));
        PipelineRunResult valid = pipeline.process(batch("BATCH-001", validOriginator(), validLms(), validBank(), List.of()));

        assertFalse(corrupt.succeeded());
        assertTrue(valid.succeeded());
        assertEquals(3, valid.snapshot().canonicalRecordFingerprints().size());
    }

    @Test
    void deterministicPhaseOnePipelineRunsEndToEndWithoutAiDependency() throws Exception {
        Path output = Files.createTempDirectory("phase1-degraded-mode");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 3, 3, 3, 0.06d, output).generate();
        PipelineBatch batch = new PipelineBatch(
            "BATCH-001",
            dataLines(generated.originatorPath()),
            dataLines(generated.lmsPath()),
            dataLines(generated.bankPath()),
            LocalDateTime.of(2026, 8, 4, 17, 30),
            List.of()
        );

        PipelineRunResult result = new Phase1PipelineService().process(batch);

        assertTrue(result.succeeded());
        assertEquals(9, result.snapshot().canonicalRecordFingerprints().size());
        assertEquals(0, result.snapshot().exceptionIds().size());
        assertEquals("CLOSE", result.snapshot().closeHoldDecision().decision().name());
    }

    @Test
    void deterministicExceptionIdIsReusedByPipelineForSameDetectionInput() {
        Phase1PipelineService pipeline = new Phase1PipelineService();
        ExceptionDetection detection = new ExceptionDetection(
            new com.vivriti.controltower.domain.CanonicalEvent(), ExceptionClassification.AMOUNT_MISMATCH,
            List.of("source.csv#line=1"), "amount-rule", "evidence", CauseConfidence.CONFIRMED,
            "source mismatch", CUTOFF, new Actor("operator-1", Role.OPERATOR));
        detection.businessEvent().setBusinessEventId("BUS-001");
        detection.businessEvent().setSourcePartner("PARA");
        detection.businessEvent().setAmount(new BigDecimal("100.00"));

        ExceptionDetection duplicate = new ExceptionDetection(
            detection.businessEvent(), detection.classification(), detection.affectedSourceRecordReferences(),
            detection.rule(), detection.evidence(), detection.causeConfidence(), detection.likelyCause(),
            detection.detectionTime(), detection.actor());
        PipelineBatch firstBatch = batch("BATCH-001", validOriginator(), validLms(), validBank(), List.of(detection));
        PipelineBatch secondBatch = batch("BATCH-001", validOriginator(), validLms(), validBank(), List.of(duplicate));

        assertEquals(pipeline.process(firstBatch).snapshot().exceptionIds(), pipeline.process(secondBatch).snapshot().exceptionIds());
    }

    private PipelineBatch batch(String id, List<String> originator, List<String> lms, List<String> bank, List<ExceptionDetection> detections) {
        return new PipelineBatch(id, originator, lms, bank, CUTOFF, detections);
    }

    private List<String> validOriginator() {
        return List.of("INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T17:05:00");
    }

    private List<String> validLms() {
        return List.of("BOOK-001,LOAN-INT-001,LOAN-001,2026-08-01T17:10:00,100.00,INR,APPROVED,BATCH-001");
    }

    private List<String> validBank() {
        return List.of("TXN-001,INSTR-001,2026-08-01T17:20:00,100.00,POSTED,,BATCH-001");
    }

    private ExceptionDetection exceptionDetection() {
        var event = new com.vivriti.controltower.domain.CanonicalEvent();
        event.setBusinessEventId("BUS-IDEMPOTENT");
        event.setSourcePartner("PARA");
        event.setAmount(new BigDecimal("25.00"));
        return new ExceptionDetection(event, ExceptionClassification.AMOUNT_MISMATCH,
            List.of("source.csv#line=1"), "amount-rule", "amount evidence", CauseConfidence.CONFIRMED,
            "Source amount differs", CUTOFF, new Actor("operator-1", Role.OPERATOR));
    }

    private List<String> dataLines(Path path) throws Exception {
        return Files.readAllLines(path).subList(1, Files.readAllLines(path).size());
    }
}