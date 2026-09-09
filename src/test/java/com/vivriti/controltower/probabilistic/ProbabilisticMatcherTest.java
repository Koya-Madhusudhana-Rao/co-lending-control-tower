package com.vivriti.controltower.probabilistic;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ProbableMatchEvidence;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ThresholdBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbabilisticMatcherTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Test
    void sameInputProducesSameScore() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(defaults(true));
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", new BigDecimal("100.00"), T);
        CanonicalEvent candidate = lms("BOOK-1", "LOAN-1", new BigDecimal("101.00"), T.plusMinutes(10));

        ProbableMatchEvidence first = matcher.evaluate(originator, candidate);
        ProbableMatchEvidence second = matcher.evaluate(originator, candidate);

        assertEquals(first.probableScore(), second.probableScore());
        assertEquals(first.contributingFieldScores(), second.contributingFieldScores());
    }

    @Test
    void scoreIsBoundedBetweenZeroAndOne() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(defaults(true));
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", new BigDecimal("100.00"), T);
        CanonicalEvent aligned = lms("BOOK-1", "LOAN-1", new BigDecimal("100.00"), T);
        CanonicalEvent disjoint = lms("BOOK-2", "ZZZZ-9", new BigDecimal("999999.00"), T.plusDays(30));

        double high = matcher.evaluate(originator, aligned).probableScore();
        double low = matcher.evaluate(originator, disjoint).probableScore();

        assertTrue(high >= 0.0 && high <= 1.0, "high=" + high);
        assertTrue(low >= 0.0 && low <= 1.0, "low=" + low);
        assertTrue(high > low);
    }

    @Test
    void bandBoundaryAtSurfaceThreshold() {
        // reference-only weighting makes the score equal to the reference similarity.
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");

        CanonicalEvent atThreshold = lms("BOOK-1", null, null, null); // sim("AB","AC") = 0.5
        atThreshold.setPartnerLoanReference("AC");
        CanonicalEvent belowThreshold = lms("BOOK-2", null, null, null); // sim("AB","XY") = 0.0
        belowThreshold.setPartnerLoanReference("XY");

        assertEquals(0.5, matcher.evaluate(originator, atThreshold).probableScore());
        assertEquals(ThresholdBand.PROBABLE_UNRESOLVED, matcher.evaluate(originator, atThreshold).thresholdBand());
        assertEquals(ThresholdBand.NOT_SURFACED, matcher.evaluate(originator, belowThreshold).thresholdBand());
    }

    @Test
    void bandBoundaryAtConfirmationThreshold() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("ABCDE");

        CanonicalEvent atConfirmation = lms("BOOK-1", null, null, null); // sim = 0.8
        atConfirmation.setPartnerLoanReference("ABCDX");
        CanonicalEvent justBelow = lms("BOOK-2", null, null, null); // sim("ABCD","ABCX") = 0.75
        justBelow.setPartnerLoanReference("ABCX");
        CanonicalEvent originatorBelow = originator("INSTR-2", null, null, null);
        originatorBelow.setPartnerLoanReference("ABCD");

        assertEquals(0.8, matcher.evaluate(originator, atConfirmation).probableScore());
        assertEquals(ThresholdBand.CONFIRMATION_ELIGIBLE, matcher.evaluate(originator, atConfirmation).thresholdBand());
        assertEquals(ThresholdBand.PROBABLE_UNRESOLVED, matcher.evaluate(originatorBelow, justBelow).thresholdBand());
    }

    @Test
    void scoreAnnotatesUnresolvedOriginatorAtOrAboveSurfaceThreshold() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent candidate = lms("BOOK-1", null, null, null);
        candidate.setPartnerLoanReference("AB"); // confirmation-strength LMS leg
        CanonicalEvent bankLeg = bank("TXN-1", "AB"); // confirmation-strength bank leg completes the group

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, candidate, bankLeg));

        assertEquals(1, surfaced.size());
        assertEquals(MatchingState.PROBABLE_MATCH, originator.getMatchingState());
        assertEquals("BOOK-1", originator.getProbableMatchEvidence().matchedCandidateReference());
        assertEquals(ReconciliationState.UNRESOLVED, reconciliationOrUnresolved(originator));
    }

    @Test
    void scoreDoesNotAnnotateBelowSurfaceThreshold() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent candidate = lms("BOOK-1", null, null, null);
        candidate.setPartnerLoanReference("XY");
        CanonicalEvent bankLeg = bank("TXN-1", "AB"); // valid bank leg; the LMS leg itself is below surface

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, candidate, bankLeg));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
        assertNull(originator.getProbableMatchEvidence());
    }

    @Test
    void level1MatchedRecordIsNeverACandidateOrOriginator() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent matchedOriginator = originator("INSTR-1", null, null, null);
        matchedOriginator.setPartnerLoanReference("AB");
        matchedOriginator.setMatchingState(MatchingState.EXACT_MATCH);
        CanonicalEvent matchedCandidate = lms("BOOK-1", null, null, null);
        matchedCandidate.setPartnerLoanReference("AC");
        matchedCandidate.setMatchingState(MatchingState.EXACT_MATCH);

        List<ProbableMatch> surfaced = matcher.score(List.of(matchedOriginator, matchedCandidate));

        assertTrue(surfaced.isEmpty());
        assertEquals(MatchingState.EXACT_MATCH, matchedOriginator.getMatchingState());
    }

    @Test
    void timingPendingCandidateIsExcluded() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent pendingCandidate = lms("BOOK-1", null, null, null);
        pendingCandidate.setPartnerLoanReference("AC");
        pendingCandidate.setMatchingState(MatchingState.TIMING_DIFFERENCE_PENDING);
        CanonicalEvent bankLeg = bank("TXN-1", "AB"); // eligible bank leg; the only LMS is pending-excluded

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, pendingCandidate, bankLeg));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
    }

    @Test
    void highestScoringCandidateIsChosenWithDeterministicIdTiebreak() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent tieHigh = lms("BOOK-9", null, null, null);
        tieHigh.setPartnerLoanReference("AB"); // sim 1.0
        CanonicalEvent tieLow = lms("BOOK-1", null, null, null);
        tieLow.setPartnerLoanReference("AB"); // sim 1.0, same score, lower id
        CanonicalEvent bankLeg = bank("TXN-1", "AB"); // confirmation-strength bank leg completes the group

        matcher.score(List.of(originator, tieHigh, tieLow, bankLeg));

        assertEquals("BOOK-1", originator.getProbableMatchEvidence().matchedCandidateReference());
    }

    @Test
    void incompleteGroupWithoutBankLegIsNotScored() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent lmsLeg = lms("BOOK-1", null, null, null);
        lmsLeg.setPartnerLoanReference("AB"); // perfect LMS leg, but no bank leg exists

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, lmsLeg));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
    }

    @Test
    void reversedBankLegIsExcludedFromCandidacy() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent lmsLeg = lms("BOOK-1", null, null, null);
        lmsLeg.setPartnerLoanReference("AB");
        CanonicalEvent reversedBank = bank("TXN-1", "AB");
        reversedBank.setSourceStatus("REVERSED");

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, lmsLeg, reversedBank));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
    }

    @Test
    void reversalLinkedBankLegIsExcludedFromCandidacy() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent lmsLeg = lms("BOOK-1", null, null, null);
        lmsLeg.setPartnerLoanReference("AB");
        CanonicalEvent reversalLinkedBank = bank("TXN-1", "AB"); // POSTED but carries reversal linkage
        reversalLinkedBank.setReversalReference("REV-1");

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, lmsLeg, reversalLinkedBank));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
    }

    @Test
    void completeNonReversedGroupRemainsEligible() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(referenceOnly(0.50, 0.80));
        CanonicalEvent originator = originator("INSTR-1", null, null, null);
        originator.setPartnerLoanReference("AB");
        CanonicalEvent lmsLeg = lms("BOOK-1", null, null, null);
        lmsLeg.setPartnerLoanReference("AB");
        CanonicalEvent bankLeg = bank("TXN-1", "AB"); // POSTED, no reversal linkage

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, lmsLeg, bankLeg));

        assertEquals(1, surfaced.size());
        assertEquals(MatchingState.PROBABLE_MATCH, originator.getMatchingState());
    }

    @Test
    void disabledConfigProducesNoResultsAndNoMutation() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(defaults(false));
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", new BigDecimal("100.00"), T);
        CanonicalEvent candidate = lms("BOOK-1", "LOAN-1", new BigDecimal("100.00"), T);

        List<ProbableMatch> surfaced = matcher.score(List.of(originator, candidate));

        assertTrue(surfaced.isEmpty());
        assertNull(originator.getMatchingState());
        assertNull(originator.getProbableMatchEvidence());
    }

    @Test
    void populatedEqualPartnerScoresHigherThanUnavailablePartner() {
        ProbabilisticMatcher matcher = new ProbabilisticMatcher(defaults(true));
        // ref=1.0, amount proximity 0.5 (gap 50 / band 100), timestamp 1.0 -> mean < 1, so a perfect partner raises it.
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", new BigDecimal("100.00"), T);
        originator.setSourcePartner("PARA");
        CanonicalEvent withPartner = lms("BOOK-1", "LOAN-1", new BigDecimal("150.00"), T);
        withPartner.setSourcePartner("PARA");
        CanonicalEvent withoutPartner = lms("BOOK-2", "LOAN-1", new BigDecimal("150.00"), T);
        withoutPartner.setSourcePartner(null);

        double withPartnerScore = matcher.evaluate(originator, withPartner).probableScore();
        double withoutPartnerScore = matcher.evaluate(originator, withoutPartner).probableScore();

        assertTrue(withPartnerScore > withoutPartnerScore,
            "withPartner=" + withPartnerScore + " withoutPartner=" + withoutPartnerScore);
    }

    @Test
    void unavailablePartnerIsNotPenalizedRelativeToOtherComponents() {
        CanonicalEvent originator = originator("INSTR-1", "LOAN-1", new BigDecimal("100.00"), T);
        originator.setSourcePartner("PARA");
        CanonicalEvent candidate = lms("BOOK-1", "LOAN-1", new BigDecimal("150.00"), T);
        candidate.setSourcePartner(null);

        double withPartnerWeight = new ProbabilisticMatcher(defaults(true)).evaluate(originator, candidate).probableScore();
        double withoutPartnerWeight = new ProbabilisticMatcher(noPartnerWeight()).evaluate(originator, candidate).probableScore();
        double refAmountTimeOnly = (0.40 * 1.0 + 0.35 * 0.5 + 0.15 * 1.0) / (0.40 + 0.35 + 0.15);

        // An unavailable partner scores exactly as if the partner weight did not exist -- no penalty.
        assertEquals(withoutPartnerWeight, withPartnerWeight, 1e-9);
        assertEquals(refAmountTimeOnly, withPartnerWeight, 1e-9);
    }

    private ReconciliationState reconciliationOrUnresolved(CanonicalEvent event) {
        return event.getReconciliationState() == null ? ReconciliationState.UNRESOLVED : event.getReconciliationState();
    }

    private ProbabilisticMatchConfig defaults(boolean enabled) {
        return new ProbabilisticMatchConfig(enabled, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80, 0.80);
    }

    private ProbabilisticMatchConfig referenceOnly(double surface, double confirmation) {
        return new ProbabilisticMatchConfig(true, 1.0, 0.0, 0.0, 0.0, new BigDecimal("100.00"), 6.0, surface, confirmation, 0.80);
    }

    private ProbabilisticMatchConfig noPartnerWeight() {
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.0, new BigDecimal("100.00"), 6.0, 0.50, 0.80, 0.80);
    }

    private CanonicalEvent originator(String id, String loanRef, BigDecimal amount, LocalDateTime timestamp) {
        return event(SourceSystem.ORIGINATOR, id, loanRef, amount, timestamp);
    }

    private CanonicalEvent lms(String id, String loanRef, BigDecimal amount, LocalDateTime timestamp) {
        return event(SourceSystem.LMS, id, loanRef, amount, timestamp);
    }

    private CanonicalEvent bank(String id, String reference) {
        CanonicalEvent event = event(SourceSystem.BANK, id, reference, new BigDecimal("100.00"), T);
        event.setSourceStatus("POSTED");
        return event;
    }

    private CanonicalEvent event(SourceSystem system, String id, String loanRef, BigDecimal amount, LocalDateTime timestamp) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(system);
        event.setImmutableSourceRecordId(id);
        event.setPartnerLoanReference(loanRef);
        event.setLoanId(loanRef);
        event.setAmount(amount);
        event.setCurrency("INR");
        event.setSourceTimestamp(timestamp);
        return event;
    }
}
