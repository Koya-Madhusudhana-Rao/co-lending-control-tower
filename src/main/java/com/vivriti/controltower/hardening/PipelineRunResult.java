package com.vivriti.controltower.hardening;

public record PipelineRunResult(String batchId, PipelineSnapshot snapshot, String failure) {
    public boolean succeeded() {
        return failure == null;
    }
}