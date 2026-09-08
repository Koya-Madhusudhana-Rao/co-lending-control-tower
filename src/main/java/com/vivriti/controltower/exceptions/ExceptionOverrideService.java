package com.vivriti.controltower.exceptions;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class ExceptionOverrideService {
    private final AppendOnlyAuditTrail auditTrail;

    public ExceptionOverrideService(AppendOnlyAuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    public OverrideResult approveStatusOverride(
        ExceptionRecord exception,
        Actor requester,
        Actor approver,
        ExceptionStatus newStatus,
        String reason,
        LocalDateTime timestamp
    ) {
        requireReason(reason);
        if (requester == null || requester.role() != Role.OPERATOR) {
            throw new IllegalArgumentException("Override requester must be an OPERATOR");
        }
        if (approver == null || approver.role() != Role.APPROVER) {
            throw new IllegalArgumentException("Override approver must be an APPROVER");
        }
        if (requester.actorId().equals(approver.actorId()) || exception.createdBy().actorId().equals(approver.actorId())) {
            throw new IllegalArgumentException("The creator or requester cannot approve the override");
        }
        if (newStatus == null || timestamp == null) {
            throw new IllegalArgumentException("Override status and timestamp are required");
        }

        ExceptionStateSnapshot before = ExceptionStateSnapshot.from(exception);
        var history = new ArrayList<>(exception.statusHistory());
        history.add(new ExceptionStatusChange(newStatus, timestamp, reason));
        ExceptionRecord afterException = new ExceptionRecord(
            exception.exceptionId(), exception.createdBy(), exception.classification(), exception.affectedSourceRecordReferences(),
            exception.businessEventId(), exception.partner(), exception.amountInr(), exception.detectionTime(), exception.age(),
            exception.priority(), exception.slaDueAt(), exception.evidence(), exception.rule(), exception.causeConfidence(),
            exception.likelyCause(), exception.owner(), exception.recommendedNextAction(), exception.escalationPath(), history,
            exception.overrideHistory());
        AuditEntry entry = new AuditEntry(
            exception.exceptionId(), "authorized-status-override", "STATUS_CHANGED_TO_" + newStatus,
            approver, timestamp, before, ExceptionStateSnapshot.from(afterException), reason);
        auditTrail.append(entry);
        return new OverrideResult(afterException, entry);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Override reason is required");
        }
    }
}