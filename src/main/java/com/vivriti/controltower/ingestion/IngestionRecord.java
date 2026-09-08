package com.vivriti.controltower.ingestion;

import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ValidationState;

public record IngestionRecord(
    int lineNumber,
    String originalRecord,
    IngestionState ingestionState,
    ValidationState validationState
) {
}