package com.vivriti.controltower.generator;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SeededFeedGeneratorTest {

    @Test
    void generatorCreatesOutputWithRequiredCounts() throws IOException {
        Path firstDir = Files.createTempDirectory("generator-seed-a");

        SeededFeedGenerator firstGenerator = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(), firstDir);

        GeneratorOutput firstOutput = firstGenerator.generate();

        assertTrue(firstOutput.originatorRows() >= 2000);
        assertTrue(firstOutput.lmsRows() >= 2000);
        assertTrue(firstOutput.bankRows() >= 1900);
        assertTrue(firstOutput.totalRows() >= 5000);

        assertTrue(Files.exists(firstOutput.groundTruthPath()));
        assertTrue(Files.exists(firstOutput.qualityReportPath()));
        assertTrue(Files.exists(firstOutput.originatorPath()));
        assertTrue(Files.exists(firstOutput.lmsPath()));
        assertTrue(Files.exists(firstOutput.bankPath()));
        // 120 is the 6% expectation, not a guarantee from per-event random selection.
        assertTrue(firstOutput.anomalyCount() >= 100);
    }

    @Test
    void generatorIsDeterministicForTheSameSeed() throws IOException {
        Path firstDir = Files.createTempDirectory("generator-seed-a");
        Path secondDir = Files.createTempDirectory("generator-seed-b");

        SeededFeedGenerator firstGenerator = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(), firstDir);
        SeededFeedGenerator secondGenerator = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(), secondDir);

        GeneratorOutput firstOutput = firstGenerator.generate();
        GeneratorOutput secondOutput = secondGenerator.generate();

        assertEquals(
            Files.readString(firstOutput.groundTruthPath()),
            Files.readString(secondOutput.groundTruthPath())
        );
        assertEquals(
            Files.readString(firstOutput.qualityReportPath()),
            Files.readString(secondOutput.qualityReportPath())
        );
        assertEquals(firstOutput.anomalyCount(), secondOutput.anomalyCount());
        assertEquals(Files.readString(firstOutput.originatorPath()), Files.readString(secondOutput.originatorPath()));
        assertEquals(Files.readString(firstOutput.lmsPath()), Files.readString(secondOutput.lmsPath()));
        assertEquals(Files.readString(firstOutput.bankPath()), Files.readString(secondOutput.bankPath()));
    }

    private double configuredAnomalyRate() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of("config", "reconciliation.yml")));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        assertNotNull(properties);
        return Double.parseDouble(properties.getProperty("reconciliation.anomalyRate"));
    }
}
