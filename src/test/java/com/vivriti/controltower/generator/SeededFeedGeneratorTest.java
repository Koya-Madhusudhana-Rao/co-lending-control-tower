package com.vivriti.controltower.generator;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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

    @Test
    void feedDataDoesNotLeakAnomalyGroundTruthLabels() throws IOException {
        Path dir = Files.createTempDirectory("generator-leakage");
        GeneratorOutput output = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(), dir).generate();

        String[] anomalyLabels = {
            "MISSING_EVENT", "DUPLICATE_EVENT", "AMOUNT_MISMATCH",
            "STATUS_MISMATCH", "TIMING_DIFFERENCE", "COMPOSITE_MATCH"
        };

        // Labels must live only in ground truth, never in the feed data the pipeline consumes.
        for (Path feed : List.of(output.originatorPath(), output.lmsPath(), output.bankPath())) {
            String content = Files.readString(feed);
            for (String label : anomalyLabels) {
                assertFalse(content.contains(label),
                    feed.getFileName() + " leaks anomaly label " + label);
            }
        }
        assertTrue(Files.readString(output.groundTruthPath()).contains("AMOUNT_MISMATCH"),
            "ground truth should still carry the anomaly labels");

        // Anomalous records must not be identifiable by an ID pattern: every originator
        // instructionId has the same INSTR-###### shape whether or not it is anomalous.
        List<String> anomalousIds = Files.readAllLines(output.groundTruthPath()).stream()
            .skip(1).map(line -> line.split(",")[0]).toList();
        assertFalse(anomalousIds.isEmpty(), "expected some anomalies to check");
        List<String> originatorIds = Files.readAllLines(output.originatorPath()).stream()
            .skip(1).map(line -> line.split(",")[0]).toList();
        for (String id : originatorIds) {
            assertTrue(id.matches("INSTR-\\d{6}"), "unexpected instruction id shape: " + id);
        }
        for (String id : anomalousIds) {
            assertTrue(originatorIds.contains(id), "anomalous id missing from originator feed: " + id);
        }
    }

    @Test
    void referenceMismatchIsAdditiveLeakFreeAndCollisionFree() throws IOException {
        GeneratorOutput baseline = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(), 0.0d,
            Files.createTempDirectory("gen-base")).generate();
        GeneratorOutput withReference = new SeededFeedGenerator(12345L, 2000, 3, 2000, configuredAnomalyRate(),
            configuredReferenceMismatchRate(), Files.createTempDirectory("gen-ref")).generate();

        // The six mandatory Phase 1 class counts are identical with and without the additive class.
        Map<String, Integer> baseCounts = anomalyCounts(baseline.groundTruthPath());
        Map<String, Integer> refCounts = anomalyCounts(withReference.groundTruthPath());
        for (String mandatory : List.of("MISSING_EVENT", "DUPLICATE_EVENT", "AMOUNT_MISMATCH", "STATUS_MISMATCH", "TIMING_DIFFERENCE", "COMPOSITE_MATCH")) {
            assertEquals(baseCounts.getOrDefault(mandatory, 0), refCounts.getOrDefault(mandatory, 0), "mandatory class changed: " + mandatory);
        }
        assertEquals(0, baseline.referenceMismatchCount());
        assertTrue(withReference.referenceMismatchCount() > 0);
        assertEquals(withReference.referenceMismatchCount(), refCounts.getOrDefault("REFERENCE_MISMATCH", 0).intValue());

        // The new label lives only in ground truth, never in the feed data.
        for (Path feed : List.of(withReference.originatorPath(), withReference.lmsPath(), withReference.bankPath())) {
            assertFalse(Files.readString(feed).contains("REFERENCE_MISMATCH"), feed.getFileName() + " leaks the label");
        }

        // Corrupted linking references collide with no real linking reference and carry the non-numeric marker.
        Set<String> realReferences = new HashSet<>();
        List<String> originator = Files.readAllLines(withReference.originatorPath());
        for (int i = 1; i < originator.size(); i++) {
            String[] columns = originator.get(i).split(",", -1);
            realReferences.add(columns[0]);
            realReferences.add(columns[1]);
        }
        List<String> corrupted = corruptedLinkingReferences(withReference);
        assertFalse(corrupted.isEmpty());
        for (String reference : corrupted) {
            assertFalse(realReferences.contains(reference), "corrupted reference collides with a real one: " + reference);
            assertTrue(reference.contains("X"), "corrupted reference should carry the non-numeric marker: " + reference);
        }
    }

    private Map<String, Integer> anomalyCounts(Path groundTruth) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        List<String> lines = Files.readAllLines(groundTruth);
        for (int i = 1; i < lines.size(); i++) {
            counts.merge(lines.get(i).split(",", -1)[5], 1, Integer::sum);
        }
        return counts;
    }

    private List<String> corruptedLinkingReferences(GeneratorOutput output) throws IOException {
        List<String> corrupted = new ArrayList<>();
        List<String> lms = Files.readAllLines(output.lmsPath());
        for (int i = 1; i < lms.size(); i++) {
            String reference = lms.get(i).split(",", -1)[2];
            if (reference.contains("X")) {
                corrupted.add(reference);
            }
        }
        List<String> bank = Files.readAllLines(output.bankPath());
        for (int i = 1; i < bank.size(); i++) {
            String reference = bank.get(i).split(",", -1)[1];
            if (reference.contains("X")) {
                corrupted.add(reference);
            }
        }
        return corrupted;
    }

    private double configuredReferenceMismatchRate() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of("config", "reconciliation.yml")));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        assertNotNull(properties);
        return Double.parseDouble(properties.getProperty("reconciliation.referenceMismatchRate"));
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
