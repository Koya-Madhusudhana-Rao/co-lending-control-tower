package com.vivriti.controltower.matching;

import java.util.List;

public record ExactMatchResult(int matchedGroups, List<com.vivriti.controltower.domain.CanonicalEvent> matchedEvents) {
    public ExactMatchResult {
        matchedEvents = List.copyOf(matchedEvents);
    }
}