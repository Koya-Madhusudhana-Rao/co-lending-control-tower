package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Measures the Phase 2 recoverable-pair gate on Seed A and writes the breakdown for review. */
class Phase2RecoverableGateTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);

    @Test
    void measuresRecoverablePairGateOnSeedA() throws Exception {
        Path gen = Files.createTempDirectory("phase2-gate-gen");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, referenceMismatchRate(), gen).generate();

        Path runs = Files.createTempDirectory("phase2-gate-runs");
        Phase1PipelineService pipeline = new Phase1PipelineService(runs, enabled());
        PipelineRunResult result = pipeline.process(batch(generated));
        String fingerprint = result.snapshot().batchFingerprint();

        List<CanonicalEvent> canonical = new DurableRunStore(runs).loadCanonicalRecords(fingerprint);
        List<GroundTruthRecord> groundTruth = new GroundTruthReader().read(generated.groundTruthPath());

        Phase2Evaluation.RecoverableGate gate = new Phase2Evaluation().recoverableGate(canonical, groundTruth);

        Path out = Path.of("target", "phase2-seed-a-recoverable-gate.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, gate.render() + "generatorReferenceMismatchCount=" + generated.referenceMismatchCount() + "\n");
        System.out.println(gate.render());

        assertTrue(gate.unresolvedOriginators() > 0, "expected a non-empty unresolved population to measure");
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
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80);
    }

    private double referenceMismatchRate() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of("config", "reconciliation.yml")));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        return Double.parseDouble(properties.getProperty("reconciliation.referenceMismatchRate"));
    }
}
