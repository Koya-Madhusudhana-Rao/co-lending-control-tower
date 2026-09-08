package com.vivriti.controltower.ingestion;

import com.vivriti.controltower.domain.ValidationState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedIngestionServiceTest {

    private final FeedIngestionService service = new FeedIngestionService();
    private final LocalDateTime cutOff = LocalDateTime.of(2026, 8, 1, 17, 30);

    @Test
    void quarantinesMissingRequiredFieldAndKeepsOtherRecords() {
        String valid = originator("INSTR-001", "2026-08-01T17:00:00", "100.00");
        String missingId = originator("", "2026-08-01T17:00:00", "100.00");

        IngestionBatchResult result = service.ingest(FeedType.ORIGINATOR, List.of(valid, missingId), "BATCH-001", new BigDecimal("200.00"), cutOff);

        assertEquals(1, result.acceptedRecords().size());
        assertEquals(ValidationState.MISSING_REQUIRED_FIELD, result.quarantinedRecords().get(0).validationState());
        assertEquals(missingId, result.quarantinedRecords().get(0).originalRecord());
    }

    @Test
    void quarantinesMalformedRowWithoutCorruptingValidRow() {
        String valid = originator("INSTR-001", "2026-08-01T17:00:00", "100.00");
        String malformed = originator("INSTR-002", "not-a-timestamp", "100.00");

        IngestionBatchResult result = service.ingest(FeedType.ORIGINATOR, List.of(valid, malformed), "BATCH-001", null, cutOff);

        assertEquals(1, result.acceptedRecords().size());
        assertEquals(ValidationState.MALFORMED_SCHEMA, result.quarantinedRecords().get(0).validationState());
    }

    @Test
    void quarantinesDuplicateRowAndPreservesBothRawRecords() {
        String first = originator("INSTR-001", "2026-08-01T17:00:00", "100.00");
        String duplicate = originator("INSTR-001", "2026-08-01T17:00:00", "100.00");

        IngestionBatchResult result = service.ingest(FeedType.ORIGINATOR, List.of(first, duplicate), "BATCH-001", null, cutOff);

        assertEquals(1, result.acceptedRecords().size());
        assertEquals(ValidationState.DUPLICATE, result.quarantinedRecords().get(0).validationState());
        assertEquals(duplicate, result.quarantinedRecords().get(0).originalRecord());
    }

    @Test
    void quarantinesInputBeyondTwoHourGraceWindow() {
        String late = originatorWithReceivedTime("INSTR-001", "2026-08-01T17:00:00", "100.00", "2026-08-01T19:31:00");

        IngestionBatchResult result = service.ingest(FeedType.ORIGINATOR, List.of(late), "BATCH-001", null, cutOff);

        assertEquals(0, result.acceptedRecords().size());
        assertEquals(ValidationState.LATE_ARRIVAL, result.quarantinedRecords().get(0).validationState());
    }

    @Test
    void acceptsValidOriginatorLmsAndBankRecords() {
        assertEquals(1, service.ingest(FeedType.ORIGINATOR, List.of(originator("INSTR-001", "2026-08-01T17:00:00", "100.00")), "BATCH-001", null, cutOff).acceptedRecords().size());
        assertEquals(1, service.ingest(FeedType.LMS, List.of("BOOK-001,LOAN-INT-001,LOAN-001,2026-08-01T17:10:00,100.00,INR,APPROVED,BATCH-001"), "BATCH-001", null, cutOff).acceptedRecords().size());
        assertEquals(1, service.ingest(FeedType.BANK, List.of("TXN-001,INSTR-001,2026-08-01T17:20:00,100.00,POSTED,,BATCH-001"), "BATCH-001", null, cutOff).acceptedRecords().size());
    }

    @Test
    void quarantinesAcceptedRowsWhenBatchTotalDoesNotMatch() {
        IngestionBatchResult result = service.ingest(
            FeedType.ORIGINATOR,
            List.of(originator("INSTR-001", "2026-08-01T17:00:00", "100.00")),
            "BATCH-001",
            new BigDecimal("99.00"),
            cutOff
        );

        assertEquals(0, result.acceptedRecords().size());
        assertEquals(ValidationState.CONTROL_TOTAL_MISMATCH, result.quarantinedRecords().get(0).validationState());
    }

    private String originator(String instructionId, String timestamp, String amount) {
        return originatorWithReceivedTime(instructionId, timestamp, amount, "2026-08-01T17:35:00");
    }

    private String originatorWithReceivedTime(String instructionId, String timestamp, String amount, String receivedTime) {
        return instructionId + ",LOAN-001,PARA," + timestamp + "," + amount + ",INR,APPROVED,BATCH-001," + receivedTime;
    }
}