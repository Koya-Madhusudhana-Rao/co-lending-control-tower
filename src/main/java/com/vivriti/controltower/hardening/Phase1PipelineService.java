package com.vivriti.controltower.hardening;

import com.vivriti.controltower.close.CloseHoldDecision;
import com.vivriti.controltower.close.CloseHoldPolicy;
import com.vivriti.controltower.close.CloseHoldService;
import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.exceptions.ExceptionQueueService;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.ingestion.FeedIngestionService;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionBatchResult;
import com.vivriti.controltower.matching.CompositeAndTimingMatcher;
import com.vivriti.controltower.matching.ExactReconciliationMatcher;
import com.vivriti.controltower.normalization.CanonicalNormalizer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class Phase1PipelineService {
    private final FeedIngestionService ingestion = new FeedIngestionService();
    private final CanonicalNormalizer normalizer = new CanonicalNormalizer();
    private final ExactReconciliationMatcher exactMatcher;
    private final CompositeAndTimingMatcher compositeAndTiming = new CompositeAndTimingMatcher();
    private final ExceptionQueueService exceptions = new ExceptionQueueService();
    private final CloseHoldService closeHold;
    private final Map<String, PipelineSnapshot> completedBatches = new java.util.HashMap<>();

    public Phase1PipelineService() {
        CloseHoldPolicy policy = CloseHoldPolicy.fromConfig(Path.of("config", "reconciliation.yml"));
        this.exactMatcher = new ExactReconciliationMatcher(new BigDecimal("1.00"));
        this.closeHold = new CloseHoldService(policy);
    }

    public synchronized PipelineRunResult process(PipelineBatch batch) {
        String fingerprint = fingerprint(batch);
        PipelineSnapshot cached = completedBatches.get(fingerprint);
        if (cached != null) {
            return new PipelineRunResult(batch.batchId(), cached, null);
        }
        try {
            List<CanonicalEvent> canonical = normalize(batch);
            exactMatcher.reconcile(canonical);
            compositeAndTiming.reconcile(canonical);
            List<ExceptionRecord> exceptionRecords = batch.detections().stream()
                .map(exceptions::create)
                .toList();
            BigDecimal batchTotal = canonical.stream().map(CanonicalEvent::getAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            CloseHoldDecision decision = closeHold.decide(batchTotal, canonical, exceptionRecords);
            PipelineSnapshot snapshot = new PipelineSnapshot(
                fingerprint,
                canonical.stream().map(this::canonicalFingerprint).sorted().toList(),
                exceptionRecords.stream().map(ExceptionRecord::exceptionId).sorted().toList(),
                decision,
                0
            );
            completedBatches.put(fingerprint, snapshot);
            return new PipelineRunResult(batch.batchId(), snapshot, null);
        } catch (RuntimeException exception) {
            return new PipelineRunResult(batch.batchId(), null, exception.getMessage());
        }
    }

    public List<PipelineRunResult> processAll(List<PipelineBatch> batches) {
        return batches.stream().map(this::process).toList();
    }

    private List<CanonicalEvent> normalize(PipelineBatch batch) {
        LocalDateTime cutoff = batch.reconciliationCutOff();
        IngestionBatchResult originator = ingestion.ingest(FeedType.ORIGINATOR, batch.originatorRecords(), batch.batchId(), null, cutoff);
        IngestionBatchResult lms = ingestion.ingest(FeedType.LMS, batch.lmsRecords(), batch.batchId(), null, cutoff);
        IngestionBatchResult bank = ingestion.ingest(FeedType.BANK, batch.bankRecords(), batch.batchId(), null, cutoff);
        if ((hasInput(batch.originatorRecords()) && originator.acceptedRecords().isEmpty())
            || (hasInput(batch.lmsRecords()) && lms.acceptedRecords().isEmpty())
            || (hasInput(batch.bankRecords()) && bank.acceptedRecords().isEmpty())) {
            throw new IllegalArgumentException("Batch has no valid records in one or more feeds");
        }
        List<CanonicalEvent> events = new ArrayList<>();
        events.addAll(normalizer.normalize(FeedType.ORIGINATOR, originator.acceptedRecords(), batch.batchId() + "/originator.csv", cutoff));
        events.addAll(normalizer.normalize(FeedType.LMS, lms.acceptedRecords(), batch.batchId() + "/lms.csv", cutoff));
        events.addAll(normalizer.normalize(FeedType.BANK, bank.acceptedRecords(), batch.batchId() + "/bank.csv", cutoff));
        return events;
    }

    private boolean hasInput(List<String> records) {
        return !records.isEmpty();
    }

    private String fingerprint(PipelineBatch batch) {
        String input = String.join("\n", batch.batchId(), String.join("\n", batch.originatorRecords()),
            String.join("\n", batch.lmsRecords()), String.join("\n", batch.bankRecords()));
        return sha256(input);
    }

    private String canonicalFingerprint(CanonicalEvent event) {
        return String.join("|", String.valueOf(event.getSourceSystem()), String.valueOf(event.getBusinessEventId()),
            String.valueOf(event.getAmount()), String.valueOf(event.getPayloadHash()), String.valueOf(event.getMatchingState()),
            String.valueOf(event.getReconciliationState()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}