package com.vivriti.controltower.hardening;

public record PipelineRunResult(String batchId, PipelineSnapshot snapshot, String failure, boolean loadedFromPersistence) {
    public boolean succeeded() {
        return failure == null;
    }
}