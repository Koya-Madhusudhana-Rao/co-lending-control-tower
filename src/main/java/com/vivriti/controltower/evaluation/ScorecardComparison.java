package com.vivriti.controltower.evaluation;

import java.util.List;

public record ScorecardComparison(
    SeedScorecard seedA,
    SeedScorecard seedB,
    List<String> materialDifferences
) {
    public ScorecardComparison {
        materialDifferences = List.copyOf(materialDifferences);
    }
}
