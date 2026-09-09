package com.vivriti.controltower.domain;

/** Evidence bundle attached to a canonical event when Level 4 scoring surfaces a probable match. */
public record ProbableMatchEvidence(
    double probableScore,
    FieldScores contributingFieldScores,
    String matchedCandidateReference,
    ThresholdBand thresholdBand,
    double surfaceThreshold,
    double confirmationThreshold
) {
}
