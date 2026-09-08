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
        thresholdInr = thresholdInr.stripTrailingZeros();
        blockingInr = blockingInr.stripTrailingZeros();
        blockingRecordReferences = List.copyOf(blockingRecordReferences);
    }

    public enum Decision {
        CLOSE,
        HOLD
    }
}