package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import com.vivriti.controltower.ingestion.FeedIngestionService;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionBatchResult;
import com.vivriti.controltower.matching.CompositeAndTimingMatcher;
import com.vivriti.controltower.matching.ExactReconciliationMatcher;
import com.vivriti.controltower.normalization.CanonicalNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScorecardComparisonTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);
    private final EvaluationService evaluation = new EvaluationService();
    private final EvaluationReporter reporter = new EvaluationReporter();

    @Test
    void seedAAndSeedBAreEvaluatedSeparatelyAndProduceAComparisonReport() throws Exception {
        SeedScorecard seedA = evaluateSeed("SEED-A", 12345L);
        SeedScorecard seedB = evaluateSeed("SEED-B", 67890L);

        ScorecardComparison comparison = reporter.compare(seedA, seedB);
        String report = reporter.render(comparison);

        assertEquals("SEED-A", comparison.seedA().seedLabel());
        assertEquals("SEED-B", comparison.seedB().seedLabel());
        assertTrue(seedA.exactMatchCount() > 0);
        assertTrue(seedB.exactMatchCount() > 0);
        assertTrue(report.contains("SEED-A"));
        assertTrue(report.contains("SEED-B"));
        assertTrue(report.contains("Material differences:"));
    }

    @Test
    void comparisonReportsMaterialDifferencesWhenScorecardsDiffer() {
        SeedScorecard seedA = evaluate("SEED-A", List.of(
            originator("INSTR-1", "100.00", true),
            originator("INSTR-2", "100.00", true)));
        SeedScorecard seedB = evaluate("SEED-B", List.of(
            originator("INSTR-1", "100.00", true),
            originator("INSTR-2", "100.00", false)));

        ScorecardComparison comparison = reporter.compare(seedA, seedB);

        assertFalse(comparison.materialDifferences().isEmpty());
    }

    @Test
    void comparisonReportsNoMaterialDifferencesForIdenticalScorecards() {
        List<CanonicalEvent> events = List.of(originator("INSTR-1", "100.00", true));
        SeedScorecard seedA = evaluate("SEED-A", events);
        SeedScorecard seedB = evaluate("SEED-B", events);

        ScorecardComparison comparison = reporter.compare(seedA, seedB);

        assertTrue(comparison.materialDifferences().isEmpty());
    }

    private SeedScorecard evaluate(String label, List<CanonicalEvent> events) {
        return evaluation.evaluate(label, new EvaluationInput(events, List.of()), List.of());
    }

    private SeedScorecard evaluateSeed(String label, long seed) throws Exception {
        Path output = Files.createTempDirectory("scorecard-" + seed);
        GeneratorOutput generated = new SeededFeedGenerator(seed, 30, 3, 30, 0.06d, output).generate();
        List<CanonicalEvent> events = reconcile(generated);
        List<GroundTruthRecord> groundTruth = new GroundTruthReader().read(generated.groundTruthPath());
        return evaluation.evaluate(label, new EvaluationInput(events, List.of()), groundTruth);
    }

    private List<CanonicalEvent> reconcile(GeneratorOutput generated) throws Exception {
        FeedIngestionService ingestion = new FeedIngestionService();
        CanonicalNormalizer normalizer = new CanonicalNormalizer();
        List<CanonicalEvent> events = new ArrayList<>();
        IngestionBatchResult originator = ingestion.ingest(FeedType.ORIGINATOR, dataLines(generated.originatorPath()), "BATCH-001", null, CUTOFF);
        IngestionBatchResult lms = ingestion.ingest(FeedType.LMS, dataLines(generated.lmsPath()), "BATCH-001", null, CUTOFF);
        IngestionBatchResult bank = ingestion.ingest(FeedType.BANK, dataLines(generated.bankPath()), "BATCH-001", null, CUTOFF);
        events.addAll(normalizer.normalize(FeedType.ORIGINATOR, originator.acceptedRecords(), "originator.csv", CUTOFF));
        events.addAll(normalizer.normalize(FeedType.LMS, lms.acceptedRecords(), "lms.csv", CUTOFF));
        events.addAll(normalizer.normalize(FeedType.BANK, bank.acceptedRecords(), "bank.csv", CUTOFF));
        new ExactReconciliationMatcher(new BigDecimal("1.00")).reconcile(events);
        new CompositeAndTimingMatcher().reconcile(events);
        return events;
    }

    private List<String> dataLines(Path path) throws Exception {
        List<String> all = Files.readAllLines(path);
        return all.subList(1, all.size());
    }

    private CanonicalEvent originator(String id, String amount, boolean matched) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(com.vivriti.controltower.domain.SourceSystem.ORIGINATOR);
        event.setBusinessEventId(id);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency("INR");
        if (matched) {
            event.setMatchingState(com.vivriti.controltower.domain.MatchingState.EXACT_MATCH);
            event.setReconciliationState(com.vivriti.controltower.domain.ReconciliationState.MATCHED);
        }
        return event;
    }
}
