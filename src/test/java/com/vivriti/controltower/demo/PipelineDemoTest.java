package com.vivriti.controltower.demo;

import com.vivriti.controltower.close.CloseHoldDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineDemoTest {

    @Test
    void seedAFullPipelineProducesHoldWithVerifiedBlockingValue() throws Exception {
        Path feeds = Files.createTempDirectory("demo-feeds");
        Path runs = Files.createTempDirectory("demo-runs");

        PipelineDemo.DemoOutput output = new PipelineDemo().run(12345L, feeds, runs);

        CloseHoldDecision decision = output.featuredDecision();
        assertEquals(CloseHoldDecision.Decision.HOLD, decision.decision());
        assertTrue(decision.blockingInr().compareTo(new BigDecimal("6051643")) == 0,
            "expected blocking INR 6051643 but was " + decision.blockingInr().toPlainString());
        assertEquals(136, decision.blockingRecordReferences().size());
    }

    @Test
    void demoProducesSeedAVersusSeedBComparisonReport() throws Exception {
        Path feeds = Files.createTempDirectory("demo-feeds");
        Path runs = Files.createTempDirectory("demo-runs");

        PipelineDemo.DemoOutput output = new PipelineDemo().run(12345L, feeds, runs);

        assertEquals("SEED-A", output.comparison().seedA().seedLabel());
        assertEquals("SEED-B", output.comparison().seedB().seedLabel());
        assertTrue(output.featuredExceptionCount() > 0);
        assertTrue(output.renderedReport().contains("SEED-A"));
        assertTrue(output.renderedReport().contains("SEED-B"));
    }
}
