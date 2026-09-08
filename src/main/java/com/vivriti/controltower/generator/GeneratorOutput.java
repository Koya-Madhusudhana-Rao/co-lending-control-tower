package com.vivriti.controltower.generator;

import java.nio.file.Path;

public record GeneratorOutput(
    Path originatorPath,
    Path lmsPath,
    Path bankPath,
    Path groundTruthPath,
    Path qualityReportPath,
    int originatorRows,
    int lmsRows,
    int bankRows,
    int totalRows,
    int anomalyCount
) {
}
