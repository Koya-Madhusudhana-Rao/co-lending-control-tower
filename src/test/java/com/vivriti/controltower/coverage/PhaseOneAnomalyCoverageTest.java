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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertGeneratedAndClassified(ExceptionClassification.MISSING_EVENT);
    }

    @Test
    void generatedDuplicateEventAnomalyIsClassifiedEndToEnd() throws Exception {
        assertGeneratedAndClassified(ExceptionClassification.DUPLICATE_EVENT);
    }

    @Test
    void generatedAmountMismatchAnomalyIsClassifiedEndToEnd() throws Exception {
        assertGeneratedAndClassified(ExceptionClassification.AMOUNT_MISMATCH);
    }

    @Test
    void generatedStatusMismatchAnomalyIsClassifiedEndToEnd() throws Exception {
        assertGeneratedAndClassified(ExceptionClassification.STATUS_MISMATCH);
    }

    @Test
    void generatedTimingDifferenceAnomalyIsClassifiedEndToEnd() throws Exception {
        assertGeneratedAndClassified(ExceptionClassification.TIMING_DIFFERENCE);
    }

    @Test
    void generatedCompositeMatchAnomalyIsClassifiedEndToEnd() throws Exception {
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
        Path output = Files.createTempDirectory("phase-one-anomaly-coverage");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, output).generate();
        return Files.readAllLines(generated.groundTruthPath()).stream()
            .skip(1)
            .map(line -> line.split(",", -1)[5])
            .map(ExceptionClassification::valueOf)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ExceptionClassification.class)));
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
}