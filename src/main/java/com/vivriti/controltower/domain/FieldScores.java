package com.vivriti.controltower.domain;

/** Per-component similarity scores that contributed to a probable match, each in [0.0, 1.0]. */
public record FieldScores(double reference, double amount, double timestamp, double partner) {
}
