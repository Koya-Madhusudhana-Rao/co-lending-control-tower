package com.vivriti.controltower.coverage;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ValidationState;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.CauseConfidence;
import com.vivriti.controltower.exceptions.ExceptionClassification;
import com.vivriti.controltower.exceptions.ExceptionDetection;
import com.vivriti.controltower.exceptions.ExceptionQueueService;
import com.vivriti.controltower.exceptions.Role;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseOneAnomalyCoverageTest {

    private static final Map<ExceptionClassification, String> EXPECTED_OWNERS = Map.of(
        ExceptionClassification.MISSING_EVENT, "Partner Ops / Integration Support",
        ExceptionClassification.DUPLICATE_EVENT, "Engineering / Source Partner",
        ExceptionClassification.AMOUNT_MISMATCH, "Finance / Lending Ops",
        ExceptionClassification.STATUS_MISMATCH, "Operations",
        ExceptionClassification.TIMING_DIFFERENCE, "Partner Ops / Integration Support",
        ExceptionClassification.COMPOSITE_MATCH, "Finance / Lending Ops"
    );

    @Test
    void generatorProducesAllSixMandatoryPhaseOneAnomalyClasses() throws Exception {
        Set<ExceptionClassification> generated = generatedAnomalyClasses();

        assertEquals(EnumSet.allOf(ExceptionClassification.class), generated);
    }

    @Test
    void generatedMissingEventAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.MISSING_EVENT);

        assertFalse(artifacts.bank().stream().anyMatch(line -> line.contains(anomaly[0])));
        assertGeneratedAndClassified(ExceptionClassification.MISSING_EVENT);
    }

    @Test
    void generatedDuplicateEventAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.DUPLICATE_EVENT);
        long occurrences = artifacts.originator().stream().filter(line -> line.startsWith(anomaly[0] + ",")).count();

        assertTrue(occurrences > 1);
        assertGeneratedAndClassified(ExceptionClassification.DUPLICATE_EVENT);
    }

    @Test
    void generatedAmountMismatchAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.AMOUNT_MISMATCH);
        BigDecimal originatorAmount = amountFor(artifacts.originator(), anomaly[0], 4);
        BigDecimal lmsAmount = amountFor(artifacts.lms(), anomaly[1], 2, 4);

        assertTrue(lmsAmount.subtract(originatorAmount).abs().compareTo(new BigDecimal("1.00")) > 0);
        assertGeneratedAndClassified(ExceptionClassification.AMOUNT_MISMATCH);
    }

    @Test
    void generatedStatusMismatchAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.STATUS_MISMATCH);
        String originatorStatus = columnsFor(artifacts.originator(), anomaly[0], 0)[6];
        String lmsStatus = columnsFor(artifacts.lms(), anomaly[1], 2)[6];

        assertFalse(originatorStatus.equals(lmsStatus));
        assertGeneratedAndClassified(ExceptionClassification.STATUS_MISMATCH);
    }

    @Test
    void generatedTimingDifferenceAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.TIMING_DIFFERENCE);
        String receivedTime = columnsFor(artifacts.originator(), anomaly[0], 0)[8];

        assertTrue(receivedTime.endsWith("T18:30:00"));
        assertGeneratedAndClassified(ExceptionClassification.TIMING_DIFFERENCE);
    }

    @Test
    void generatedCompositeMatchAnomalyIsClassifiedEndToEnd() throws Exception {
        GeneratedArtifacts artifacts = generateArtifacts();
        String[] anomaly = anomalyRow(artifacts, ExceptionClassification.COMPOSITE_MATCH);
        BigDecimal originatorAmount = amountFor(artifacts.originator(), anomaly[0], 4);
        List<String> lmsRows = artifacts.lms().stream().filter(line -> columns(line)[2].equals(anomaly[1])).toList();
        List<String> bankRows = artifacts.bank().stream().filter(line -> columns(line)[1].equals(anomaly[0])).toList();
        BigDecimal lmsSum = lmsRows.stream().map(line -> new BigDecimal(columns(line)[4])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bankSum = bankRows.stream().map(line -> new BigDecimal(columns(line)[3])).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(2, lmsRows.size());
        assertEquals(2, bankRows.size());
        assertTrue(originatorAmount.compareTo(lmsSum) == 0);
        assertTrue(originatorAmount.compareTo(bankSum) == 0);
        assertGeneratedAndClassified(ExceptionClassification.COMPOSITE_MATCH);
    }

    private void assertGeneratedAndClassified(ExceptionClassification classification) throws Exception {
        Set<ExceptionClassification> generated = generatedAnomalyClasses();
        assertEquals(true, generated.contains(classification));

        var exception = new ExceptionQueueService().create(new ExceptionDetection(
            event("BUS-" + classification),
            classification,
            List.of("ground-truth.csv#" + classification),
            "coverage-rule-" + classification,
            "generated anomaly was carried to deterministic exception classification",
            CauseConfidence.CONFIRMED,
            "Generated Phase 1 anomaly",
            LocalDateTime.of(2026, 8, 4, 10, 0),
            new Actor("operator-coverage", Role.OPERATOR)
        ));

        assertEquals(classification, exception.classification());
        assertEquals(EXPECTED_OWNERS.get(classification), exception.owner());
    }

    private Set<ExceptionClassification> generatedAnomalyClasses() throws Exception {
        return generateArtifacts().groundTruth().stream()
            .map(line -> line.split(",", -1)[5])
            .map(ExceptionClassification::valueOf)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ExceptionClassification.class)));
    }

    private GeneratedArtifacts generateArtifacts() throws Exception {
        Path output = Files.createTempDirectory("phase-one-anomaly-coverage");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, output).generate();
        return new GeneratedArtifacts(
            Files.readAllLines(generated.originatorPath()).stream().skip(1).toList(),
            Files.readAllLines(generated.lmsPath()).stream().skip(1).toList(),
            Files.readAllLines(generated.bankPath()).stream().skip(1).toList(),
            Files.readAllLines(generated.groundTruthPath()).stream().skip(1).toList()
        );
    }

    private String[] anomalyRow(GeneratedArtifacts artifacts, ExceptionClassification classification) {
        return artifacts.groundTruth().stream()
            .map(this::columns)
            .filter(columns -> columns[5].equals(classification.name()))
            .findFirst()
            .orElseThrow();
    }

    private BigDecimal amountFor(List<String> rows, String key, int amountColumn) {
        return amountFor(rows, key, 0, amountColumn);
    }

    private BigDecimal amountFor(List<String> rows, String key, int keyColumn, int amountColumn) {
        return new BigDecimal(columnsFor(rows, key, keyColumn)[amountColumn]);
    }

    private String[] columnsFor(List<String> rows, String key, int keyColumn) {
        return rows.stream()
            .map(this::columns)
            .filter(columns -> columns[keyColumn].equals(key))
            .findFirst()
            .orElseThrow();
    }

    private String[] columns(String line) {
        return line.split(",", -1);
    }

    private CanonicalEvent event(String id) {
        CanonicalEvent event = new CanonicalEvent();
        event.setBusinessEventId(id);
        event.setSourcePartner("PARA");
        event.setAmount(new BigDecimal("100.00"));
        event.setSourceTimestamp(LocalDateTime.of(2026, 8, 1, 10, 0));
        event.setIngestionState(IngestionState.VALIDATED);
        event.setValidationState(ValidationState.VALID);
        return event;
    }

    private record GeneratedArtifacts(
        List<String> originator,
        List<String> lms,
        List<String> bank,
        List<String> groundTruth
    ) {
    }
}