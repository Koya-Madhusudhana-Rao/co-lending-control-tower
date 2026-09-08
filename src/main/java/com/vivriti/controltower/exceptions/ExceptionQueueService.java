package com.vivriti.controltower.exceptions;

import com.vivriti.controltower.domain.CanonicalEvent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class ExceptionQueueService {

    private static final Map<ExceptionClassification, String> OWNERS = Map.of(
        ExceptionClassification.MISSING_EVENT, "Partner Ops / Integration Support",
        ExceptionClassification.DUPLICATE_EVENT, "Engineering / Source Partner",
        ExceptionClassification.AMOUNT_MISMATCH, "Finance / Lending Ops",
        ExceptionClassification.STATUS_MISMATCH, "Operations",
        ExceptionClassification.TIMING_DIFFERENCE, "Partner Ops / Integration Support",
        ExceptionClassification.COMPOSITE_MATCH, "Finance / Lending Ops"
    );

    public ExceptionRecord create(ExceptionDetection detection) {
        CanonicalEvent event = detection.businessEvent();
        if (event.getMatchingState() != null || event.getReconciliationState() != null) {
            throw new IllegalArgumentException("Only unset events can enter the exception queue");
        }
        String exceptionId = deterministicId(detection);
        Duration age = age(event, detection.detectionTime());
        String owner = OWNERS.get(detection.classification());
        return new ExceptionRecord(
            exceptionId,
            detection.classification(),
            detection.affectedSourceRecordReferences(),
            event.getBusinessEventId(),
            event.getSourcePartner(),
            event.getAmount(),
            detection.detectionTime(),
            age,
            priority(detection.classification()),
            detection.detectionTime().plus(sla(detection.classification())),
            detection.evidence(),
            detection.rule(),
            detection.causeConfidence(),
            detection.likelyCause(),
            owner,
            nextAction(detection.classification()),
            escalation(owner),
            List.of(new ExceptionStatusChange(ExceptionStatus.OPEN, detection.detectionTime(), "Detected by " + detection.rule())),
            List.of()
        );
    }

    private Duration age(CanonicalEvent event, LocalDateTime detectionTime) {
        return event.getSourceTimestamp() == null
            ? Duration.ZERO
            : Duration.between(event.getSourceTimestamp(), detectionTime);
    }

    private String deterministicId(ExceptionDetection detection) {
        String businessEventId = detection.businessEvent().getBusinessEventId() == null
            ? "UNKNOWN" : detection.businessEvent().getBusinessEventId();
        String material = businessEventId + "|" + detection.classification() + "|"
            + String.join("|", detection.affectedSourceRecordReferences().stream().sorted().toList());
        try {
            return "EXC-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String priority(ExceptionClassification classification) {
        return classification == ExceptionClassification.AMOUNT_MISMATCH ? "HIGH" : "MEDIUM";
    }

    private Duration sla(ExceptionClassification classification) {
        return classification == ExceptionClassification.AMOUNT_MISMATCH ? Duration.ofHours(4) : Duration.ofHours(8);
    }

    private String nextAction(ExceptionClassification classification) {
        return switch (classification) {
            case MISSING_EVENT -> "Request the missing partner event and verify feed delivery.";
            case DUPLICATE_EVENT -> "Validate source idempotency and suppress the duplicate.";
            case AMOUNT_MISMATCH -> "Reconcile source amounts and obtain Finance/Lending Ops confirmation.";
            case STATUS_MISMATCH -> "Compare source statuses and confirm the operational state.";
            case TIMING_DIFFERENCE -> "Confirm late-arrival evidence and monitor the partner response.";
            case COMPOSITE_MATCH -> "Review the composite relationship and component evidence.";
        };
    }

    private String escalation(String owner) {
        return owner + " -> Finance Control Tower Lead";
    }
}