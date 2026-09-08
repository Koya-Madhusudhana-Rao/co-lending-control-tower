package com.vivriti.controltower.matching;

import com.vivriti.controltower.domain.CanonicalEvent;

import java.util.List;

public record CompositeAndTimingResult(
    int compositeGroups,
    List<CanonicalEvent> timingEvents,
    List<CanonicalEvent> compositeEvents
) {
    public CompositeAndTimingResult {
        timingEvents = List.copyOf(timingEvents);
        compositeEvents = List.copyOf(compositeEvents);
    }
}