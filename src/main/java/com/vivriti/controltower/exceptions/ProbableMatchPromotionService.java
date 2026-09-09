package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.ThresholdBand;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Two-actor human confirmation/rejection of a probable match, mirroring ExceptionOverrideService's segregation-of-duties rules. */
public class ProbableMatchPromotionService {

    private static final String RULE = "probabilistic-match-v1";

    private final AppendOnlyAuditTrail auditTrail;

    public ProbableMatchPromotionService(AppendOnlyAuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    /** Promote a confirmation-eligible probable match to reconciled; mutates the record's matching/reconciliation state only via this authorized action. */
    public ProbablePromotionResult confirm(
        ExceptionRecord exception,
        CanonicalEvent probableRecord,
        Actor requester,
        Actor approver,
        String reason,
        LocalDateTime timestamp
    ) {
        validateActors(requester, approver, exception);
        requireReason(reason);
        requireTimestamp(timestamp);
        ProbableMatchEvidence evidence = requireEligible(probableRecord);

        ExceptionStateSnapshot before = ExceptionStateSnapshot.from(exception);
        var statusHistory = new ArrayList<>(exception.statusHistory());
        statusHistory.add(new ExceptionStatusChange(ExceptionStatus.RESOLVED, timestamp, reason));

        String decision = "PROMOTED_PROBABLE_TO_RECONCILED";
        String auditReference = exception.exceptionId() + ":" + decision + "@" + timestamp;
        var overrideHistory = new ArrayList<>(exception.overrideHistory());
        overrideHistory.add("approvedBy=" + approver.actorId()
            + "; decision=" + decision
            + "; at=" + timestamp
            + "; reason=" + reason
            + "; auditRef=" + auditReference);

        ExceptionRecord promoted = withHistory(exception, statusHistory, overrideHistory);

        // Score alone never reconciles; only this authorized action promotes the state.
        probableRecord.setMatchingState(MatchingState.PROBABLE_MATCH_CONFIRMED);
        probableRecord.setReconciliationState(ReconciliationState.MATCHED);

        AuditEntry entry = new AuditEntry(
            input(exception, probableRecord, evidence), rule(evidence), decision,
            approver, timestamp, before, ExceptionStateSnapshot.from(promoted), reason);
        auditTrail.append(entry);
        return new ProbablePromotionResult(promoted, entry);
    }

    /** Reject a proposed probable match: audit only, leaving the record unresolved and the exception unchanged. */
    public ProbablePromotionResult reject(
        ExceptionRecord exception,
        CanonicalEvent probableRecord,
        Actor requester,
        Actor approver,
        String reason,
        LocalDateTime timestamp
    ) {
        validateActors(requester, approver, exception);
        requireReason(reason);
        requireTimestamp(timestamp);
        ProbableMatchEvidence evidence = probableRecord.getProbableMatchEvidence();

        ExceptionStateSnapshot state = ExceptionStateSnapshot.from(exception);
        AuditEntry entry = new AuditEntry(
            input(exception, probableRecord, evidence), rule(evidence), "REJECTED_PROBABLE",
            approver, timestamp, state, state, reason);
        auditTrail.append(entry);
        return new ProbablePromotionResult(exception, entry);
    }

    private ProbableMatchEvidence requireEligible(CanonicalEvent probableRecord) {
        if (probableRecord.getMatchingState() != MatchingState.PROBABLE_MATCH) {
            throw new IllegalArgumentException("Only a PROBABLE_MATCH record may be promoted");
        }
        ProbableMatchEvidence evidence = probableRecord.getProbableMatchEvidence();
        if (evidence == null || evidence.thresholdBand() != ThresholdBand.CONFIRMATION_ELIGIBLE) {
            throw new IllegalArgumentException("Only confirmation-eligible probable matches may be promoted");
        }
        return evidence;
    }

    private void validateActors(Actor requester, Actor approver, ExceptionRecord exception) {
        if (requester == null || requester.role() != Role.OPERATOR) {
            throw new IllegalArgumentException("Promotion requester must be an OPERATOR");
        }
        if (approver == null || approver.role() != Role.APPROVER) {
            throw new IllegalArgumentException("Promotion approver must be an APPROVER");
        }
        if (requester.actorId().equals(approver.actorId())
            || exception.createdBy().actorId().equals(approver.actorId())) {
            throw new IllegalArgumentException("The creator or requester cannot approve the promotion");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Promotion reason is required");
        }
    }

    private void requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Promotion timestamp is required");
        }
    }

    private String input(ExceptionRecord exception, CanonicalEvent record, ProbableMatchEvidence evidence) {
        String candidate = evidence == null ? "none" : evidence.matchedCandidateReference();
        return "businessEventId=" + record.getBusinessEventId()
            + "; exceptionId=" + exception.exceptionId()
            + "; candidateRef=" + candidate;
    }

    private String rule(ProbableMatchEvidence evidence) {
        if (evidence == null) {
            return RULE;
        }
        return RULE
            + "; score=" + evidence.probableScore()
            + "; reference=" + evidence.contributingFieldScores().reference()
            + "; amount=" + evidence.contributingFieldScores().amount()
            + "; timestamp=" + evidence.contributingFieldScores().timestamp()
            + "; partner=" + evidence.contributingFieldScores().partner();
    }

    private ExceptionRecord withHistory(ExceptionRecord e, List<ExceptionStatusChange> statusHistory, List<String> overrideHistory) {
        return new ExceptionRecord(
            e.exceptionId(), e.createdBy(), e.classification(), e.affectedSourceRecordReferences(),
            e.businessEventId(), e.partner(), e.amountInr(), e.detectionTime(), e.age(),
            e.priority(), e.slaDueAt(), e.evidence(), e.rule(), e.causeConfidence(),
            e.likelyCause(), e.owner(), e.recommendedNextAction(), e.escalationPath(), statusHistory, overrideHistory);
    }
}
