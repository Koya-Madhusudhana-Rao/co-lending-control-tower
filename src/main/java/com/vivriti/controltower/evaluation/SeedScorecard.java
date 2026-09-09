package com.vivriti.controltower.evaluation;

import java.math.BigDecimal;
import java.util.List;

public record SeedScorecard(
    String seedLabel,
    int businessEventCount,
    int exactMatchCount,
    BigDecimal exactMatchInr,
    int compositeMatchCount,
    BigDecimal compositeMatchInr,
    int falseMatchCount,
    BigDecimal falseMatchInr,
    List<String> falseMatchReferences,
    double exceptionCoverageRate,
    double straightThroughRate,
    boolean controlTotalIntegrity,
    BigDecimal batchTotalInr,
    BigDecimal matchedInr,
    BigDecimal pendingInr,
    BigDecimal unresolvedInr,
    List<String> unresolvedReferences
) {
    public SeedScorecard {
        falseMatchReferences = List.copyOf(falseMatchReferences);
        unresolvedReferences = List.copyOf(unresolvedReferences);
    }
}
