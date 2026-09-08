package com.vivriti.controltower.exceptions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ExceptionRecord(
    String exceptionId,
    ExceptionClassification classification,
    List<String> affectedSourceRecordReferences,
    String businessEventId,
    String partner,
    BigDecimal amountInr,
    LocalDateTime detectionTime,
    Duration age,
    String priority,
    LocalDateTime slaDueAt,
    String evidence,
    String rule,
    CauseConfidence causeConfidence,
    String likelyCause,
    String owner,
    String recommendedNextAction,
    String escalationPath,
    List<ExceptionStatusChange> statusHistory,
    List<String> overrideHistory
) {
    public ExceptionRecord {
        affectedSourceRecordReferences = List.copyOf(affectedSourceRecordReferences);
        statusHistory = List.copyOf(statusHistory);
        overrideHistory = List.copyOf(overrideHistory);
    }
}