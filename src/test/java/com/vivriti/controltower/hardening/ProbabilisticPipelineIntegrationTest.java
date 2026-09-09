package com.vivriti.controltower.hardening;

import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import com.vivriti.controltower.probabilistic.ProbabilisticMatchConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves Level 4 is a no-op when disabled (byte-identical Phase 1 output) and never alters the financial surface when enabled. */
class ProbabilisticPipelineIntegrationTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);

    @Test
    void disabledModeIsByteIdenticalAndEnablingNeverChangesFinancialOutput() throws Exception {
        Path gen = Files.createTempDirectory("prob-gen");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 300, 3, 300, 0.06d, gen).generate();

        String disabledRoot = runAndReturnDir(generated, disabled());
        String enabledRoot = runAndReturnDir(generated, enabled());

        // Financial surface (exceptions + close/hold decision) must be byte-identical regardless of the flag.
        assertEquals(read(disabledRoot, "exception-records.json"), read(enabledRoot, "exception-records.json"));
        assertEquals(read(disabledRoot, "close-hold-decision.json"), read(enabledRoot, "close-hold-decision.json"));

        // Disabled mode must never produce a probable-match annotation; enabled mode must.
        assertFalse(read(disabledRoot, "canonical-records.json").contains("PROBABLE_MATCH"),
            "disabled mode must not annotate probable matches");
        assertTrue(read(enabledRoot, "canonical-records.json").contains("PROBABLE_MATCH"),
            "enabled mode should surface at least one probable match on this population");
    }

    private String runAndReturnDir(GeneratorOutput generated, ProbabilisticMatchConfig config) throws Exception {
        Path runs = Files.createTempDirectory("prob-runs");
        PipelineBatch batch = new PipelineBatch("BATCH-001",
            dataLines(generated.originatorPath()), dataLines(generated.lmsPath()), dataLines(generated.bankPath()),
            CUTOFF, List.of());
        PipelineRunResult result = new Phase1PipelineService(runs, config).process(batch);
        assertTrue(result.succeeded());
        return runs.resolve(result.snapshot().batchFingerprint()).toString();
    }

    private String read(String directory, String file) throws Exception {
        return Files.readString(Path.of(directory).resolve(file));
    }

    private ProbabilisticMatchConfig disabled() {
        return new ProbabilisticMatchConfig(false, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80);
    }

    private ProbabilisticMatchConfig enabled() {
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80);
    }

    private List<String> dataLines(Path path) throws Exception {
        List<String> all = Files.readAllLines(path);
        return all.subList(1, all.size());
    }
}
