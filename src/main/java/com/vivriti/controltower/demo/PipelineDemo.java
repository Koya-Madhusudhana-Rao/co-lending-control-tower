package com.vivriti.controltower.demo;

import com.vivriti.controltower.close.CloseHoldDecision;
import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.ValidationState;
import com.vivriti.controltower.evaluation.EvaluationInput;
import com.vivriti.controltower.evaluation.EvaluationReporter;
import com.vivriti.controltower.evaluation.EvaluationService;
import com.vivriti.controltower.evaluation.GroundTruthReader;
import com.vivriti.controltower.evaluation.GroundTruthRecord;
import com.vivriti.controltower.evaluation.ScorecardComparison;
import com.vivriti.controltower.evaluation.SeedScorecard;
import com.vivriti.controltower.exceptions.ExceptionMaterializer;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import com.vivriti.controltower.hardening.Phase1PipelineService;
import com.vivriti.controltower.hardening.PipelineBatch;
import com.vivriti.controltower.hardening.PipelineRunResult;
import com.vivriti.controltower.ingestion.FeedIngestionService;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionBatchResult;
import com.vivriti.controltower.matching.CompositeAndTimingMatcher;
import com.vivriti.controltower.matching.ExactReconciliationMatcher;
import com.vivriti.controltower.normalization.CanonicalNormalizer;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Testable end-to-end demonstration core; reuses the pipeline and evaluation services without duplicating their logic. */
public class PipelineDemo {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);
    private static final long SEED_A = 12345L;
    private static final long SEED_B = 67890L;
    private static final int ORIGINATOR_ROWS = 2000;
    private static final int PARTNERS = 3;
    private static final int LMS_ROWS = 2000;
    private static final double ANOMALY_RATE = 0.06d;

    public record DemoOutput(
        long featuredSeed,
        CloseHoldDecision featuredDecision,
        int featuredExceptionCount,
        String featuredFingerprint,
        ScorecardComparison comparison,
        String renderedReport
    ) {
    }

    public DemoOutput run(long primarySeed, Path feedsRoot, Path runsRoot) throws Exception {
        GeneratorOutput seedAFeeds = generate(SEED_A, feedsRoot.resolve("seed-a"));
        GeneratorOutput seedBFeeds = generate(SEED_B, feedsRoot.resolve("seed-b"));
        SeedScorecard scoreA = scorecard("SEED-A", seedAFeeds);
        SeedScorecard scoreB = scorecard("SEED-B", seedBFeeds);
        ScorecardComparison comparison = new EvaluationReporter().compare(scoreA, scoreB);

        GeneratorOutput featuredFeeds = primarySeed == SEED_A ? seedAFeeds
            : primarySeed == SEED_B ? seedBFeeds
            : generate(primarySeed, feedsRoot.resolve("seed-" + primarySeed));
        PipelineRunResult result = new Phase1PipelineService(runsRoot).process(batch(featuredFeeds));
        if (!result.succeeded()) {
            throw new IllegalStateException("Demo pipeline failed: " + result.failure());
        }

        return new DemoOutput(
            primarySeed,
            result.snapshot().closeHoldDecision(),
            result.snapshot().exceptionIds().size(),
            result.snapshot().batchFingerprint(),
            comparison,
            new EvaluationReporter().render(comparison));
    }

    private GeneratorOutput generate(long seed, Path feedsDir) throws Exception {
        Files.createDirectories(feedsDir);
        return new SeededFeedGenerator(seed, ORIGINATOR_ROWS, PARTNERS, LMS_ROWS, ANOMALY_RATE, feedsDir).generate();
    }

    private PipelineBatch batch(GeneratorOutput generated) throws Exception {
        return new PipelineBatch(
            "BATCH-001",
            dataLines(generated.originatorPath()),
            dataLines(generated.lmsPath()),
            dataLines(generated.bankPath()),
            CUTOFF,
            List.of());
    }

    private SeedScorecard scorecard(String label, GeneratorOutput generated) throws Exception {
        FeedIngestionService ingestion = new FeedIngestionService();
        CanonicalNormalizer normalizer = new CanonicalNormalizer();
        IngestionBatchResult originator = ingestion.ingest(FeedType.ORIGINATOR, dataLines(generated.originatorPath()), "BATCH-001", null, CUTOFF);
        IngestionBatchResult lms = ingestion.ingest(FeedType.LMS, dataLines(generated.lmsPath()), "BATCH-001", null, CUTOFF);
        IngestionBatchResult bank = ingestion.ingest(FeedType.BANK, dataLines(generated.bankPath()), "BATCH-001", null, CUTOFF);

        List<CanonicalEvent> events = new ArrayList<>();
        events.addAll(normalizer.normalize(FeedType.ORIGINATOR, originator.acceptedRecords(), "originator.csv", CUTOFF));
        events.addAll(normalizer.normalize(FeedType.LMS, lms.acceptedRecords(), "lms.csv", CUTOFF));
        events.addAll(normalizer.normalize(FeedType.BANK, bank.acceptedRecords(), "bank.csv", CUTOFF));

        new ExactReconciliationMatcher(new BigDecimal("1.00")).reconcile(events);
        new CompositeAndTimingMatcher().reconcile(events);

        List<String> duplicates = originator.quarantinedRecords().stream()
            .filter(record -> record.validationState() == ValidationState.DUPLICATE)
            .map(record -> record.originalRecord().split(",", -1)[0])
            .toList();
        List<ExceptionRecord> exceptions = new ExceptionMaterializer().materialize(events, duplicates, CUTOFF);
        List<GroundTruthRecord> groundTruth = new GroundTruthReader().read(generated.groundTruthPath());
        return new EvaluationService().evaluate(label, new EvaluationInput(events, exceptions), groundTruth);
    }

    private List<String> dataLines(Path path) throws Exception {
        List<String> all = Files.readAllLines(path);
        return all.subList(1, all.size());
    }
}
