package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.exceptions.ExceptionRecord;

import java.util.List;

public record EvaluationInput(
    List<CanonicalEvent> canonicalEvents,
    List<ExceptionRecord> exceptions
) {
    public EvaluationInput {
        canonicalEvents = List.copyOf(canonicalEvents);
        exceptions = List.copyOf(exceptions);
    }
}
