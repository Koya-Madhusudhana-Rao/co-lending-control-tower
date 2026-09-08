package com.vivriti.controltower.hardening;

import com.vivriti.controltower.close.CloseHoldDecision;

import java.util.List;

public record PipelineSnapshot(
    String batchFingerprint,
    List<String> canonicalRecordFingerprints,
    List<String> exceptionIds,
    CloseHoldDecision closeHoldDecision,
    int auditEntryCount
) {
    public PipelineSnapshot {
        canonicalRecordFingerprints = List.copyOf(canonicalRecordFingerprints);
        exceptionIds = List.copyOf(exceptionIds);
    }
}