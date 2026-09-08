package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;

import java.time.LocalDateTime;
import java.util.List;

public record ExceptionDetection(
    CanonicalEvent businessEvent,
    ExceptionClassification classification,
    List<String> affectedSourceRecordReferences,
    String rule,
    String evidence,
    CauseConfidence causeConfidence,
    String likelyCause,
    LocalDateTime detectionTime,
    Actor actor
) {
    public ExceptionDetection {
        affectedSourceRecordReferences = List.copyOf(affectedSourceRecordReferences);
    }
}