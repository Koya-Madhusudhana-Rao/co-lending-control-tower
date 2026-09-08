package com.vivriti.controltower.exceptions;

import java.time.LocalDateTime;

public record AuditEntry(
    String input,
    String rule,
    String decision,
    Actor actor,
    LocalDateTime timestamp,
    ExceptionStateSnapshot beforeState,
    ExceptionStateSnapshot afterState,
    String reason
) {
    public AuditEntry {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Audit reason is required");
        }
    }
}