package com.vivriti.controltower.hardening;

import com.vivriti.controltower.exceptions.ExceptionDetection;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineBatch(
    String batchId,
    List<String> originatorRecords,
    List<String> lmsRecords,
    List<String> bankRecords,
    LocalDateTime reconciliationCutOff,
    List<ExceptionDetection> detections
) {
    public PipelineBatch {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("Batch ID is required");
        }
        originatorRecords = List.copyOf(originatorRecords);
        lmsRecords = List.copyOf(lmsRecords);
        bankRecords = List.copyOf(bankRecords);
        detections = List.copyOf(detections);
    }
}