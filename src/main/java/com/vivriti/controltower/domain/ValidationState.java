package com.vivriti.controltower.domain;

public enum ValidationState {
    VALID,
    INVALID,
    DUPLICATE,
    MISSING_REQUIRED_FIELD,
    MALFORMED_SCHEMA,
    CONTROL_TOTAL_MISMATCH,
    LATE_ARRIVAL
}
