package com.vivriti.controltower.evaluation;

import java.math.BigDecimal;

public record GroundTruthRecord(
    String instructionId,
    BigDecimal amount,
    String currency,
    String anomalyType,
    String status
) {
}
