package com.vivriti.controltower.close;

import java.math.BigDecimal;
import java.util.List;

public record CloseHoldDecision(
    Decision decision,
    BigDecimal thresholdInr,
    BigDecimal blockingInr,
    List<String> blockingRecordReferences
) {
    public CloseHoldDecision {
        blockingRecordReferences = List.copyOf(blockingRecordReferences);
    }

    public enum Decision {
        CLOSE,
        HOLD
    }
}