package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import com.vivriti.controltower.hardening.DurableRunStore;
import com.vivriti.controltower.hardening.Phase1PipelineService;
import com.vivriti.controltower.hardening.PipelineBatch;
import com.vivriti.controltower.hardening.PipelineRunResult;
import com.vivriti.controltower.probabilistic.ProbabilisticMatchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Full 15d evaluation: Phase 2 precision/recall on Seed A (tune) and fresh Seed B (frozen config), plus Phase 1 §9 re-verification on both. */
class Phase2EvaluationTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);
    private static final double CONFIRMATION_THRESHOLD = 0.80;

    @Test
    void seedAAndSeedBPhase2PrecisionRecallAndPhase1Reverification() throws Exception {
        StringBuilder report = new StringBuilder();
        SeedScorecard phase1A = evaluateSeed(12345L, "SEED-A", report);
        SeedScorecard phase1B = evaluateSeed(67890L, "SEED-B", report);

        Path out = Path.of("target", "phase2-evaluation.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        Files.createDirectories(Path.of("reports", "phase2"));
        Files.writeString(Path.of("reports", "phase2", "phase2-evaluation.txt"), report.toString());
        System.out.println(report);

        // Phase 1 gate must still hold on both seeds with the new class present.
        for (SeedScorecard phase1 : List.of(phase1A, phase1B)) {
            assertEquals(0, phase1.falseMatchCount(), "false-match exposure must stay zero");
            assertTrue(phase1.exceptionCoverageRate() > 0.9999, "exception coverage must stay 1.0");
            assertTrue(phase1.controlTotalIntegrity(), "control-total integrity must hold");
        }
    }

    private SeedScorecard evaluateSeed(long seed, String label, StringBuilder report) throws Exception {
        Path gen = Files.createTempDirectory("phase2-eval-gen-" + seed);
        GeneratorOutput generated = new SeededFeedGenerator(seed, 2000, 3, 2000, 0.06d, referenceMismatchRate(), gen).generate();

        Path runs = Files.createTempDirectory("phase2-eval-runs-" + seed);
        PipelineRunResult result = new Phase1PipelineService(runs, enabled()).process(batch(generated));
        String fingerprint = result.snapshot().batchFingerprint();

        DurableRunStore store = new DurableRunStore(runs);
        List<CanonicalEvent> canonical = store.loadCanonicalRecords(fingerprint);
        List<ExceptionRecord> exceptions = store.loadExceptions(fingerprint);
        List<GroundTruthRecord> groundTruth = new GroundTruthReader().read(generated.groundTruthPath());

        Phase2Evaluation.Phase2Scorecard phase2 = new Phase2Evaluation()
            .evaluate(seed, canonical, groundTruth, CONFIRMATION_THRESHOLD);
        SeedScorecard phase1 = new EvaluationService()
            .evaluate(label, new EvaluationInput(canonical, exceptions), groundTruth);

        assertTrue(phase2.referenceMismatchTotal() >= 30,
            "expected a meaningful recoverable population, got " + phase2.referenceMismatchTotal());

        report.append("############ ").append(label).append(" (seed=").append(seed).append(") ############\n");
        report.append(phase2.render());
        report.append("--- Phase 1 re-verification (").append(label).append(") ---\n");
        report.append("exactMatchCount=").append(phase1.exactMatchCount()).append("\n");
        report.append("compositeMatchCount=").append(phase1.compositeMatchCount()).append("\n");
        report.append("falseMatchCount=").append(phase1.falseMatchCount()).append("\n");
        report.append("exceptionCoverageRate=").append(phase1.exceptionCoverageRate()).append("\n");
        report.append("straightThroughRate=").append(phase1.straightThroughRate()).append("\n");
        report.append("controlTotalIntegrity=").append(phase1.controlTotalIntegrity()).append("\n");
        report.append("unresolvedInr=").append(phase1.unresolvedInr().toPlainString()).append("\n");
        report.append("referenceMismatchInGenerator=").append(generated.referenceMismatchCount()).append("\n\n");
        return phase1;
    }

    private PipelineBatch batch(GeneratorOutput generated) throws Exception {
        return new PipelineBatch("BATCH-001",
            dataLines(generated.originatorPath()), dataLines(generated.lmsPath()), dataLines(generated.bankPath()),
            CUTOFF, List.of());
    }

    private List<String> dataLines(Path path) throws Exception {
        List<String> all = Files.readAllLines(path);
        return all.subList(1, all.size());
    }

    private ProbabilisticMatchConfig enabled() {
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, CONFIRMATION_THRESHOLD);
    }

    private double referenceMismatchRate() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of("config", "reconciliation.yml")));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        return Double.parseDouble(properties.getProperty("reconciliation.referenceMismatchRate"));
    }
}
