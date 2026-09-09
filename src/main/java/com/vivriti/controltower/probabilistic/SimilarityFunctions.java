package com.vivriti.controltower.probabilistic;

import com.vivriti.controltower.domain.CanonicalEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** Pure, side-effect-free similarity components. No ground-truth or evaluation dependencies. */
final class SimilarityFunctions {

    private SimilarityFunctions() {
    }

    /** Best normalized-Levenshtein similarity across the available reference field pairs. */
    static double referenceSimilarity(CanonicalEvent originator, CanonicalEvent candidate) {
        double best = normalizedSimilarity(originator.getPartnerLoanReference(), candidate.getPartnerLoanReference());
        best = Math.max(best, normalizedSimilarity(originator.getBusinessEventId(), candidate.getCorrelationId()));
        best = Math.max(best, normalizedSimilarity(originator.getLoanId(), candidate.getLoanId()));
        return best;
    }

    static double amountProximity(BigDecimal a, BigDecimal b, BigDecimal bandInr) {
        if (a == null || b == null || bandInr == null || bandInr.signum() <= 0) {
            return 0.0;
        }
        double ratio = a.subtract(b).abs().doubleValue() / bandInr.doubleValue();
        return ratio >= 1.0 ? 0.0 : 1.0 - ratio;
    }

    static double timestampProximity(LocalDateTime a, LocalDateTime b, double bandHours) {
        if (a == null || b == null || bandHours <= 0.0) {
            return 0.0;
        }
        double gapHours = Math.abs(Duration.between(a, b).toMinutes()) / 60.0;
        double ratio = gapHours / bandHours;
        return ratio >= 1.0 ? 0.0 : 1.0 - ratio;
    }

    static double partnerAgreement(CanonicalEvent a, CanonicalEvent b) {
        String pa = a.getSourcePartner();
        String pb = b.getSourcePartner();
        return pa != null && pa.equals(pb) ? 1.0 : 0.0;
    }

    /** Partner can only be compared when both sides carry a partner; otherwise the component is excluded from scoring. */
    static boolean partnerComparable(CanonicalEvent a, CanonicalEvent b) {
        return a.getSourcePartner() != null && b.getSourcePartner() != null;
    }

    static double normalizedSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshtein(a, b) / longest);
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
