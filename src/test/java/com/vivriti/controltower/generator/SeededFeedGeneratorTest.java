package com.vivriti.controltower.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SeededFeedGeneratorTest {

    @Test
    void generatorCreatesDeterministicOutputWithRequiredCounts() throws IOException {
        Path firstDir = Files.createTempDirectory("generator-seed-a");
        Path secondDir = Files.createTempDirectory("generator-seed-b");

        SeededFeedGenerator firstGenerator = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, firstDir);
        SeededFeedGenerator secondGenerator = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, secondDir);

        GeneratorOutput firstOutput = firstGenerator.generate();
        GeneratorOutput secondOutput = secondGenerator.generate();

        assertEquals(2000, firstOutput.originatorRows());
        assertEquals(2000, firstOutput.lmsRows());
        assertEquals(2000, firstOutput.bankRows());
        assertEquals(6000, firstOutput.totalRows());

        assertTrue(Files.exists(firstOutput.groundTruthPath()));
        assertTrue(Files.exists(firstOutput.qualityReportPath()));
        assertTrue(Files.exists(firstOutput.originatorPath()));
        assertTrue(Files.exists(firstOutput.lmsPath()));
        assertTrue(Files.exists(firstOutput.bankPath()));

        assertEquals(
            Files.readString(firstOutput.groundTruthPath()),
            Files.readString(secondOutput.groundTruthPath())
        );
        assertEquals(
            Files.readString(firstOutput.qualityReportPath()),
            Files.readString(secondOutput.qualityReportPath())
        );

        assertTrue(firstOutput.anomalyCount() >= 120);
    }
}
