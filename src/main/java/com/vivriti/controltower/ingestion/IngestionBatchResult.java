package com.vivriti.controltower.ingestion;

import java.math.BigDecimal;
import java.util.List;

public record IngestionBatchResult(
    List<IngestionRecord> acceptedRecords,
    List<IngestionRecord> quarantinedRecords,
    BigDecimal acceptedAmount,
    BigDecimal observedAmount
) {
    public IngestionBatchResult {
        acceptedRecords = List.copyOf(acceptedRecords);
        quarantinedRecords = List.copyOf(quarantinedRecords);
    }
}