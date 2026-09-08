package com.vivriti.controltower.normalization;

import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ValidationState;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CanonicalNormalizerTest {

    private final CanonicalNormalizer normalizer = new CanonicalNormalizer();
    private final LocalDateTime cutOff = LocalDateTime.of(2026, 8, 1, 17, 30);

    @Test
    void normalizesAllThreeSourceSchemasWithLineage() {
        var originator = normalizer.normalize(FeedType.ORIGINATOR, List.of(record(
            FeedType.ORIGINATOR, "INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T17:05:00")), "originator.csv", cutOff).get(0);
        var lms = normalizer.normalize(FeedType.LMS, List.of(record(
            FeedType.LMS, "BOOK-001,LOAN-INT-001,LOAN-001,2026-08-01T17:10:00,100.00,INR,APPROVED,BATCH-001")), "lms.csv", cutOff).get(0);
        var bank = normalizer.normalize(FeedType.BANK, List.of(record(
            FeedType.BANK, "TXN-001,INSTR-001,2026-08-01T17:20:00,100.00,POSTED,,BATCH-001")), "bank.csv", cutOff).get(0);

        assertEquals("ORIGINATOR", originator.getSourceSystem().name());
        assertEquals("INSTR-001", originator.getImmutableSourceRecordId());
        assertEquals("DISBURSEMENT_INSTRUCTION", originator.getEventType());
        assertEquals("LMS", lms.getSourceSystem().name());
        assertEquals("BOOK-001", lms.getImmutableSourceRecordId());
        assertEquals("LOAN_BOOKING", lms.getEventType());
        assertEquals("BANK", bank.getSourceSystem().name());
        assertEquals("TXN-001", bank.getImmutableSourceRecordId());
        assertEquals("SETTLEMENT", bank.getEventType());
        assertEquals("INR", bank.getCurrency());
    }

    @Test
    void tracesCanonicalEventToExactRawRowWithHashAndLocation() throws Exception {
        String raw = "INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T18:30:00";
        var event = normalizer.normalize(FeedType.ORIGINATOR, List.of(record(FeedType.ORIGINATOR, raw)), "originator.csv", cutOff).get(0);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(raw.getBytes(StandardCharsets.UTF_8)));

        assertEquals(IngestionState.VALIDATED, event.getIngestionState());
        assertEquals(ValidationState.VALID, event.getValidationState());
        assertEquals("originator.csv#line=1", event.getRawSourceLocation());
        assertEquals(expectedHash, event.getPayloadHash());
    }

    @Test
    void carriesLateValidationWithoutPredecidingMatchingOrReconciliation() {
        String raw = "INSTR-001,LOAN-001,PARA,2026-08-01T17:00:00,100.00,INR,APPROVED,BATCH-001,2026-08-01T18:30:00";
        var event = normalizer.normalize(FeedType.ORIGINATOR, List.of(record(
            FeedType.ORIGINATOR, raw, ValidationState.LATE_ARRIVAL)), "originator.csv", cutOff).get(0);

        assertEquals(IngestionState.VALIDATED, event.getIngestionState());
        assertEquals(ValidationState.LATE_ARRIVAL, event.getValidationState());
        assertNull(event.getMatchingState());
        assertNull(event.getReconciliationState());
    }

    private IngestionRecord record(FeedType feedType, String raw) {
        return record(feedType, raw, ValidationState.VALID);
    }

    private IngestionRecord record(FeedType feedType, String raw, ValidationState validationState) {
        return new IngestionRecord(1, raw, IngestionState.VALIDATED, validationState);
    }
}