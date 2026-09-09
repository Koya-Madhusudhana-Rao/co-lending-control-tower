package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Materializes exception records from reconciliation residue so unresolved value never leaves the queue; uses evidence only, never ground truth. */
public class ExceptionMaterializer {

    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");
    private static final Actor SYSTEM = new Actor("system-materializer", Role.OPERATOR);

    private final ExceptionQueueService queue = new ExceptionQueueService();

    public List<ExceptionRecord> materialize(
        List<CanonicalEvent> events,
        List<String> quarantinedDuplicateBusinessEventIds,
        LocalDateTime detectionTime
    ) {
        List<ExceptionRecord> records = new ArrayList<>();
        for (CanonicalEvent originator : originators(events)) {
            if (isMatched(originator) || isPending(originator)) {
                continue;
            }
            ExceptionClassification classification = classify(originator, events);
            records.add(queue.create(detection(originator, classification, detectionTime)));
        }
        for (String businessEventId : new TreeSet<>(quarantinedDuplicateBusinessEventIds)) {
            records.add(queue.create(detection(duplicatePlaceholder(businessEventId, events),
                ExceptionClassification.DUPLICATE_EVENT, detectionTime)));
        }
        return List.copyOf(records);
    }

    private ExceptionClassification classify(CanonicalEvent originator, List<CanonicalEvent> events) {
        List<CanonicalEvent> lms = related(events, SourceSystem.LMS, originator.getPartnerLoanReference(), CanonicalEvent::getPartnerLoanReference);
        List<CanonicalEvent> bank = related(events, SourceSystem.BANK, originator.getBusinessEventId(), CanonicalEvent::getCorrelationId);
        if (lms.isEmpty() || bank.isEmpty()) {
            return ExceptionClassification.MISSING_EVENT;
        }
        if (originator.getValidationState() == ValidationState.LATE_ARRIVAL) {
            return ExceptionClassification.TIMING_DIFFERENCE;
        }
        BigDecimal lmsSum = sum(lms);
        BigDecimal bankSum = sum(bank);
        boolean currencyMismatch = lms.stream().anyMatch(event -> !agree(originator.getCurrency(), event.getCurrency()))
            || bank.stream().anyMatch(event -> !agree(originator.getCurrency(), event.getCurrency()));
        boolean amountMismatch = differsBeyondTolerance(originator.getAmount(), lmsSum)
            || differsBeyondTolerance(originator.getAmount(), bankSum);
        if (currencyMismatch || amountMismatch) {
            return ExceptionClassification.AMOUNT_MISMATCH;
        }
        boolean statusMismatch = lms.stream().anyMatch(event -> !agree(originator.getSourceStatus(), event.getSourceStatus()))
            || bank.stream().anyMatch(event -> !"POSTED".equals(event.getSourceStatus()));
        if (statusMismatch) {
            return ExceptionClassification.STATUS_MISMATCH;
        }
        return ExceptionClassification.AMOUNT_MISMATCH;
    }

    private ExceptionDetection detection(CanonicalEvent event, ExceptionClassification classification, LocalDateTime detectionTime) {
        String reference = event.getRawSourceLocation() != null ? event.getRawSourceLocation() : String.valueOf(event.getBusinessEventId());
        return new ExceptionDetection(event, classification, List.of(reference),
            "reconciliation-residual-" + classification,
            "materialized from unresolved reconciliation evidence",
            CauseConfidence.INFERRED, "Derived from source evidence without ground truth",
            detectionTime, SYSTEM);
    }

    private CanonicalEvent duplicatePlaceholder(String businessEventId, List<CanonicalEvent> events) {
        CanonicalEvent survivor = events.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(event -> businessEventId.equals(event.getBusinessEventId()))
            .findFirst()
            .orElse(null);
        CanonicalEvent placeholder = new CanonicalEvent();
        placeholder.setBusinessEventId(businessEventId);
        placeholder.setSourcePartner(survivor == null ? null : survivor.getSourcePartner());
        placeholder.setAmount(survivor == null ? BigDecimal.ZERO : survivor.getAmount());
        placeholder.setCurrency(survivor == null ? "INR" : survivor.getCurrency());
        placeholder.setRawSourceLocation(survivor == null ? null : survivor.getRawSourceLocation());
        placeholder.setSourceTimestamp(survivor == null ? null : survivor.getSourceTimestamp());
        return placeholder;
    }

    private List<CanonicalEvent> originators(List<CanonicalEvent> events) {
        return events.stream().filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR).toList();
    }

    private List<CanonicalEvent> related(
        List<CanonicalEvent> events,
        SourceSystem sourceSystem,
        String key,
        java.util.function.Function<CanonicalEvent, String> keyExtractor
    ) {
        if (key == null) {
            return List.of();
        }
        return events.stream()
            .filter(event -> event.getSourceSystem() == sourceSystem)
            .filter(event -> key.equals(keyExtractor.apply(event)))
            .toList();
    }

    private boolean isMatched(CanonicalEvent event) {
        return event.getMatchingState() == MatchingState.EXACT_MATCH
            || event.getMatchingState() == MatchingState.COMPOSITE_MATCH;
    }

    private boolean isPending(CanonicalEvent event) {
        return event.getMatchingState() == MatchingState.TIMING_DIFFERENCE_PENDING
            || event.getReconciliationState() == ReconciliationState.PENDING;
    }

    private BigDecimal sum(List<CanonicalEvent> events) {
        return events.stream()
            .map(event -> event.getAmount() == null ? BigDecimal.ZERO : event.getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean differsBeyondTolerance(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return true;
        }
        return first.subtract(second).abs().compareTo(TOLERANCE) > 0;
    }

    private boolean agree(String first, String second) {
        return first != null && first.equals(second);
    }
}
