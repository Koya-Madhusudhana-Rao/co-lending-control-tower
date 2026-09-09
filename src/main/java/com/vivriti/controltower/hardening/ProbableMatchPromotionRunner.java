package com.vivriti.controltower.hardening;

import com.vivriti.controltower.close.CloseHoldDecision;
import com.vivriti.controltower.close.CloseHoldPolicy;
import com.vivriti.controltower.close.CloseHoldService;
import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.exceptions.Actor;
import com.vivriti.controltower.exceptions.AppendOnlyAuditTrail;
import com.vivriti.controltower.exceptions.AuditEntry;
import com.vivriti.controltower.exceptions.ExceptionRecord;
import com.vivriti.controltower.exceptions.ProbableMatchPromotionService;
import com.vivriti.controltower.exceptions.ProbablePromotionResult;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads a persisted run, applies a human confirm/reject to a probable match, recomputes close/hold on confirm, and re-persists. */
public class ProbableMatchPromotionRunner {

    private final DurableRunStore durableRunStore;
    private final CloseHoldService closeHold;

    public ProbableMatchPromotionRunner(Path runsRoot) {
        this.durableRunStore = new DurableRunStore(runsRoot);
        this.closeHold = new CloseHoldService(CloseHoldPolicy.fromConfig(Path.of("config", "reconciliation.yml")));
    }

    public CloseHoldDecision confirm(
        String fingerprint, String businessEventId,
        Actor requester, Actor approver, String reason, LocalDateTime timestamp
    ) {
        List<CanonicalEvent> canonical = durableRunStore.loadCanonicalRecords(fingerprint);
        List<ExceptionRecord> exceptions = new ArrayList<>(durableRunStore.loadExceptions(fingerprint));
        List<AuditEntry> existingAudits = durableRunStore.loadAudits(fingerprint);

        CanonicalEvent target = probableOriginator(canonical, businessEventId);
        int index = exceptionIndexFor(exceptions, businessEventId);

        AppendOnlyAuditTrail trail = new AppendOnlyAuditTrail();
        ProbablePromotionResult result = new ProbableMatchPromotionService(trail)
            .confirm(exceptions.get(index), target, requester, approver, reason, timestamp);
        exceptions.set(index, result.exception());

        CloseHoldDecision decision = recompute(canonical, exceptions);
        persist(fingerprint, canonical, exceptions, existingAudits, trail.entries(), decision);
        return decision;
    }

    public void reject(
        String fingerprint, String businessEventId,
        Actor requester, Actor approver, String reason, LocalDateTime timestamp
    ) {
        List<CanonicalEvent> canonical = durableRunStore.loadCanonicalRecords(fingerprint);
        List<ExceptionRecord> exceptions = durableRunStore.loadExceptions(fingerprint);
        List<AuditEntry> existingAudits = durableRunStore.loadAudits(fingerprint);

        CanonicalEvent target = probableOriginator(canonical, businessEventId);
        ExceptionRecord exception = exceptions.get(exceptionIndexFor(exceptions, businessEventId));

        AppendOnlyAuditTrail trail = new AppendOnlyAuditTrail();
        new ProbableMatchPromotionService(trail).reject(exception, target, requester, approver, reason, timestamp);

        // Reject changes no reconciliation state, so the persisted decision is left as-is.
        persist(fingerprint, canonical, exceptions, existingAudits, trail.entries(), durableRunStore.loadDecision(fingerprint));
    }

    private CloseHoldDecision recompute(List<CanonicalEvent> canonical, List<ExceptionRecord> exceptions) {
        BigDecimal batchTotal = canonical.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .map(CanonicalEvent::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return closeHold.decide(batchTotal, canonical, exceptions);
    }

    private void persist(
        String fingerprint, List<CanonicalEvent> canonical, List<ExceptionRecord> exceptions,
        List<AuditEntry> existingAudits, List<AuditEntry> newAudits, CloseHoldDecision decision
    ) {
        List<AuditEntry> combined = new ArrayList<>(existingAudits);
        combined.addAll(newAudits);
        durableRunStore.save(fingerprint, canonical, exceptions, combined, decision);
    }

    private CanonicalEvent probableOriginator(List<CanonicalEvent> canonical, String businessEventId) {
        return canonical.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(event -> event.getMatchingState() == MatchingState.PROBABLE_MATCH)
            .filter(event -> businessEventId.equals(event.getBusinessEventId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No probable-match originator for " + businessEventId));
    }

    private int exceptionIndexFor(List<ExceptionRecord> exceptions, String businessEventId) {
        for (int i = 0; i < exceptions.size(); i++) {
            if (businessEventId.equals(exceptions.get(i).businessEventId())) {
                return i;
            }
        }
        throw new IllegalArgumentException("No exception for business event " + businessEventId);
    }
}
