package com.vivriti.controltower.domain;

public enum ThresholdBand {
    NOT_SURFACED,
    PROBABLE_UNRESOLVED,
    CONFIRMATION_ELIGIBLE;

    public static ThresholdBand classify(double score, double surfaceThreshold, double confirmationThreshold) {
        if (score < surfaceThreshold) {
            return NOT_SURFACED;
        }
        if (score < confirmationThreshold) {
            return PROBABLE_UNRESOLVED;
        }
        return CONFIRMATION_ELIGIBLE;
    }
}
