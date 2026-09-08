package com.vivriti.controltower.exceptions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ExceptionStateSnapshot(
    String exceptionId,
    Actor createdBy,
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
    public ExceptionStateSnapshot {
        affectedSourceRecordReferences = List.copyOf(affectedSourceRecordReferences);
        statusHistory = List.copyOf(statusHistory);
        overrideHistory = List.copyOf(overrideHistory);
    }

    public static ExceptionStateSnapshot from(ExceptionRecord exception) {
        return new ExceptionStateSnapshot(exception.exceptionId(), exception.createdBy(), exception.classification(),
            exception.affectedSourceRecordReferences(), exception.businessEventId(), exception.partner(), exception.amountInr(),
            exception.detectionTime(), exception.age(), exception.priority(), exception.slaDueAt(), exception.evidence(),
            exception.rule(), exception.causeConfidence(), exception.likelyCause(), exception.owner(),
            exception.recommendedNextAction(), exception.escalationPath(), exception.statusHistory(), exception.overrideHistory());
    }
}