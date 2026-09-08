package com.vivriti.controltower.normalization;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.ingestion.FeedType;
import com.vivriti.controltower.ingestion.IngestionRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class CanonicalNormalizer {

    public List<CanonicalEvent> normalize(
        FeedType feedType,
        List<IngestionRecord> acceptedRecords,
        String rawSourceLocation,
        LocalDateTime reconciliationCutOff
    ) {
        List<CanonicalEvent> events = new ArrayList<>();
        for (IngestionRecord record : acceptedRecords) {
            events.add(normalizeRecord(feedType, record, rawSourceLocation, reconciliationCutOff));
        }
        return List.copyOf(events);
    }

    private CanonicalEvent normalizeRecord(
        FeedType feedType,
        IngestionRecord record,
        String rawSourceLocation,
        LocalDateTime reconciliationCutOff
    ) {
        String[] columns = record.originalRecord().split(",", -1);
        String identifier = columns[feedType.identifierColumn()];
        LocalDateTime sourceTimestamp = LocalDateTime.parse(columns[feedType.timestampColumn()]);
        LocalDateTime receivedTimestamp = LocalDateTime.parse(columns[feedType.arrivalTimestampColumn()]);
        BigDecimal amount = new BigDecimal(columns[feedType.amountColumn()]);
        String batch = columns[feedType.batchColumn()];
        String status = status(feedType, columns);

        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(sourceSystem(feedType));
        event.setSourcePartner(sourcePartner(feedType, columns));
        event.setBatchId(batch);
        event.setImmutableSourceRecordId(identifier);
        event.setBusinessEventId(identifier);
        event.setCorrelationId(correlationId(feedType, columns));
        event.setLoanId(loanId(feedType, columns));
        event.setPartnerLoanReference(partnerLoanReference(feedType, columns));
        event.setEventType(eventType(feedType, columns));
        event.setBusinessDate(sourceTimestamp.toLocalDate());
        event.setSourceTimestamp(sourceTimestamp);
        event.setReceivedTimestamp(receivedTimestamp);
        event.setReconciliationCutOff(reconciliationCutOff);
        event.setAmount(amount);
        event.setCurrency(currency(feedType, columns));
        event.setFinancialComponents("principal=" + amount);
        event.setSourceStatus(status);
        event.setCanonicalStatus(status);
        event.setParentReference(parentReference(feedType, columns));
        event.setReversalReference(reversalReference(feedType, columns));
        event.setRawSourceLocation(rawSourceLocation + "#line=" + record.lineNumber());
        event.setPayloadHash(sha256(record.originalRecord()));
        event.setIngestionState(record.ingestionState());
        event.setValidationState(record.validationState());
        event.setMatchingState(record.validationState() == com.vivriti.controltower.domain.ValidationState.LATE_ARRIVAL
            ? MatchingState.TIMING_DIFFERENCE_PENDING
            : MatchingState.UNMATCHED);
        event.setReconciliationState(ReconciliationState.PENDING);
        return event;
    }

    private SourceSystem sourceSystem(FeedType feedType) {
        return switch (feedType) {
            case ORIGINATOR -> SourceSystem.ORIGINATOR;
            case LMS -> SourceSystem.LMS;
            case BANK -> SourceSystem.BANK;
        };
    }

    private String sourcePartner(FeedType feedType, String[] columns) {
        return feedType == FeedType.ORIGINATOR ? columns[2] : null;
    }

    private String correlationId(FeedType feedType, String[] columns) {
        return switch (feedType) {
            case ORIGINATOR -> columns[1];
            case LMS -> columns[2];
            case BANK -> columns[1];
        };
    }

    private String loanId(FeedType feedType, String[] columns) {
        return switch (feedType) {
            case ORIGINATOR -> columns[1];
            case LMS -> columns[1];
            case BANK -> columns[1];
        };
    }

    private String partnerLoanReference(FeedType feedType, String[] columns) {
        return switch (feedType) {
            case ORIGINATOR -> columns[1];
            case LMS -> columns[2];
            case BANK -> columns[1];
        };
    }

    private String eventType(FeedType feedType, String[] columns) {
        return switch (feedType) {
            case ORIGINATOR -> "DISBURSEMENT_INSTRUCTION";
            case LMS -> "LOAN_BOOKING";
            case BANK -> columns[4].equals("REVERSED") ? "SETTLEMENT_REVERSAL" : "SETTLEMENT";
        };
    }

    private String currency(FeedType feedType, String[] columns) {
        return feedType == FeedType.BANK ? "INR" : columns[5];
    }

    private String status(FeedType feedType, String[] columns) {
        return feedType == FeedType.BANK ? columns[4] : columns[6];
    }

    private String parentReference(FeedType feedType, String[] columns) {
        return feedType == FeedType.BANK ? columns[1] : null;
    }

    private String reversalReference(FeedType feedType, String[] columns) {
        return feedType == FeedType.BANK ? columns[5] : null;
    }

    private String sha256(String rawRecord) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawRecord.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}