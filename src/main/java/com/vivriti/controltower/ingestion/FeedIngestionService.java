package com.vivriti.controltower.ingestion;

import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ValidationState;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedIngestionService {

    private static final Duration GRACE_WINDOW = Duration.ofHours(2);

    public IngestionBatchResult ingest(
        FeedType feedType,
        List<String> rawRecords,
        String expectedBatch,
        BigDecimal expectedBatchTotal,
        LocalDateTime reconciliationCutOff
    ) {
        List<IngestionRecord> accepted = new ArrayList<>();
        List<IngestionRecord> quarantined = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        BigDecimal observedAmount = BigDecimal.ZERO;
        BigDecimal acceptedAmount = BigDecimal.ZERO;

        for (int index = 0; index < rawRecords.size(); index++) {
            String rawRecord = rawRecords.get(index);
            int lineNumber = index + 1;
            String[] columns = rawRecord == null ? new String[0] : rawRecord.split(",", -1);
            ValidationState validationState = validateShape(feedType, columns);
            BigDecimal parsedAmount = parseAmount(feedType, columns);
            if (parsedAmount != null) {
                observedAmount = observedAmount.add(parsedAmount);
            }

            if (validationState == null) {
                try {
                    LocalDateTime.parse(columns[feedType.timestampColumn()]);
                    LocalDateTime arrivalTime = LocalDateTime.parse(columns[feedType.arrivalTimestampColumn()]);
                    String identifier = columns[feedType.identifierColumn()];
                    String batch = columns[feedType.batchColumn()];

                    if (!expectedBatch.equals(batch)) {
                        validationState = ValidationState.MALFORMED_SCHEMA;
                    } else if (!identifiers.add(identifier)) {
                        validationState = ValidationState.DUPLICATE;
                    } else if (arrivalTime.isAfter(reconciliationCutOff.plus(GRACE_WINDOW))) {
                        validationState = ValidationState.LATE_ARRIVAL;
                    } else {
                        validationState = ValidationState.VALID;
                        acceptedAmount = acceptedAmount.add(parsedAmount);
                    }
                } catch (RuntimeException exception) {
                    validationState = ValidationState.MALFORMED_SCHEMA;
                }
            }

            IngestionRecord record = new IngestionRecord(
                lineNumber,
                rawRecord,
                validationState == ValidationState.VALID ? IngestionState.VALIDATED : IngestionState.QUARANTINED,
                validationState
            );
            if (validationState == ValidationState.VALID) {
                accepted.add(record);
            } else {
                quarantined.add(record);
            }
        }

        if (expectedBatchTotal != null && expectedBatchTotal.compareTo(observedAmount) != 0) {
            for (int index = 0; index < accepted.size(); index++) {
                IngestionRecord record = accepted.get(index);
                accepted.set(index, new IngestionRecord(
                    record.lineNumber(),
                    record.originalRecord(),
                    IngestionState.QUARANTINED,
                    ValidationState.CONTROL_TOTAL_MISMATCH
                ));
                quarantined.add(accepted.remove(index));
                index--;
            }
            acceptedAmount = BigDecimal.ZERO;
        }

        return new IngestionBatchResult(accepted, quarantined, acceptedAmount, observedAmount);
    }

    private BigDecimal parseAmount(FeedType feedType, String[] columns) {
        if (columns.length <= feedType.amountColumn() || columns[feedType.amountColumn()].isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(columns[feedType.amountColumn()]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ValidationState validateShape(FeedType feedType, String[] columns) {
        if (columns.length == 0 || columns.length != feedType.columnCount()) {
            return ValidationState.MALFORMED_SCHEMA;
        }
        int[] requiredColumns = {
            feedType.identifierColumn(),
            feedType.amountColumn(),
            feedType.timestampColumn(),
            feedType.batchColumn()
        };
        for (int column : requiredColumns) {
            if (columns[column].isBlank()) {
                return ValidationState.MISSING_REQUIRED_FIELD;
            }
        }
        return null;
    }
}