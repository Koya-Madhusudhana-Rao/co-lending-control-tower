package com.vivriti.controltower.exceptions;

/** Outcome of a probable-match confirm/reject: the (possibly updated) exception and the audit entry written. */
public record ProbablePromotionResult(ExceptionRecord exception, AuditEntry auditEntry) {
}
