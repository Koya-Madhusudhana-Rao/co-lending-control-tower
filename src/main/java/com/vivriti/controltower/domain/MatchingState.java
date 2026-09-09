package com.vivriti.controltower.domain;

public enum MatchingState {
    UNMATCHED,
    EXACT_MATCH,
    COMPOSITE_MATCH,
    TIMING_DIFFERENCE_PENDING,
    PROBABLE_MATCH,
    PROBABLE_MATCH_CONFIRMED,
    UNRESOLVED
}
