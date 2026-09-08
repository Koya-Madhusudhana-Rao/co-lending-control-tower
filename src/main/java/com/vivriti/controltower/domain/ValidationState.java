package com.vivriti.controltower.domain;

public enum ValidationState {
    VALID,
    INVALID,
    DUPLICATE,
    MISSING_REQUIRED_FIELD,
    MALFORMED_SCHEMA,
    LATE_ARRIVAL
}
