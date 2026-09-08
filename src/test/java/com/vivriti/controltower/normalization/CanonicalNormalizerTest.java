package com.vivriti.controltower.normalization;

import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.ValidationState;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CanonicalNormalizerTest {

    private final CanonicalNormalizer normalizer = new CanonicalNormalizer();
    private final LocalDateTime cutOff = LocalDateTime.of(2026, 8, 1, 17, 30);

    @Test
    void normalizesAllThreeSourceSchemasWithLineage() {
        assertEquals("ORIGINATOR", normalizer.normalize(FeedType.ORIGINATOR, List.of(record(
            FeedType.ORIGINATOR, "INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T17:05:00")), "originator.csv", cutOff).get(0).getSourceSystem().name());
        assertEquals("LMS", normalizer.normalize(FeedType.LMS, List.of(record(
            FeedType.LMS, "BOOK-001,LOAN-INT-001,LOAN-001,2026-08-01T17:10:00,100.00,INR,APPROVED,BATCH-001")), "lms.csv", cutOff).get(0).getSourceSystem().name());
        assertEquals("BANK", normalizer.normalize(FeedType.BANK, List.of(record(
            FeedType.BANK, "TXN-001,INSTR-001,2026-08-01T17:20:00,100.00,POSTED,,BATCH-001")), "bank.csv", cutOff).get(0).getSourceSystem().name());
    }

    @Test
    void preservesRawLineLocationHashAndLatePendingState() {
        String raw = "INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T18:30:00";
        var event = normalizer.normalize(FeedType.ORIGINATOR, List.of(record(FeedType.ORIGINATOR, raw, ValidationState.LATE_ARRIVAL)), "originator.csv", cutOff).get(0);

        assertEquals(IngestionState.VALIDATED, event.getIngestionState());
        assertEquals(ValidationState.LATE_ARRIVAL, event.getValidationState());
        assertEquals(MatchingState.TIMING_DIFFERENCE_PENDING, event.getMatchingState());
        assertEquals(ReconciliationState.PENDING, event.getReconciliationState());
        assertEquals("originator.csv#line=1", event.getRawSourceLocation());
        assertNotNull(event.getPayloadHash());
        assertEquals(64, event.getPayloadHash().length());
    }

    private IngestionRecord record(FeedType feedType, String raw) {
        return record(feedType, raw, ValidationState.VALID);
    }

    private IngestionRecord record(FeedType feedType, String raw, ValidationState validationState) {
        return new IngestionRecord(1, raw, IngestionState.VALIDATED, validationState);
    }
}