package com.vivriti.controltower.matching;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.IngestionState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExactReconciliationMatcherTest {

    private final ExactReconciliationMatcher matcher = new ExactReconciliationMatcher(configuredTolerance());

    @Test
    void matchesCleanOriginatorLmsAndBankRelationship() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");

        ExactMatchResult result = matcher.reconcile(List.of(originator, lms, bank));

        assertEquals(1, result.matchedGroups());
        assertEquals(3, result.matchedEvents().size());
        assertEquals(com.vivriti.controltower.domain.MatchingState.EXACT_MATCH, originator.getMatchingState());
        assertEquals(ReconciliationState.MATCHED, bank.getReconciliationState());
    }

    @Test
    void leavesEventsUnsetWhenAmountExceedsOneInrTolerance() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "101.01", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");

        assertNoMatch(originator, lms, bank);
    }

    @Test
    void acceptsAmountDifferenceExactlyAtOneInrTolerance() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "101.00", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");

        ExactMatchResult result = matcher.reconcile(List.of(originator, lms, bank));

        assertEquals(1, result.matchedGroups());
        assertEquals(com.vivriti.controltower.domain.MatchingState.EXACT_MATCH, lms.getMatchingState());
    }

    @Test
    void leavesEventsUnsetWhenCurrencyDisagrees() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "100.00", "USD");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");

        assertNoMatch(originator, lms, bank);
    }

    @Test
    void leavesEventsUnsetWhenSourceStatusesDisagree() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");
        lms.setSourceStatus("BOOKING_REVIEW");

        assertNoMatch(originator, lms, bank);
    }

    @Test
    void leavesLateArrivalRecordsUnsetForTimingLevel() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, "INSTR-001", "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");
        originator.setValidationState(ValidationState.LATE_ARRIVAL);

        assertNoMatch(originator, lms, bank);
    }

    @Test
    void leavesEventsUnsetWhenIdentifierCannotLinkSources() {
        CanonicalEvent originator = event(SourceSystem.ORIGINATOR, null, "LOAN-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent lms = event(SourceSystem.LMS, "BOOK-001", "LOAN-INT-001", "LOAN-001", "100.00", "INR");
        CanonicalEvent bank = event(SourceSystem.BANK, "TXN-001", "INSTR-001", "INSTR-001", "100.00", "INR");

        assertNoMatch(originator, lms, bank);
    }

    private void assertNoMatch(CanonicalEvent originator, CanonicalEvent lms, CanonicalEvent bank) {
        ExactMatchResult result = matcher.reconcile(List.of(originator, lms, bank));

        assertEquals(0, result.matchedGroups());
        assertEquals(0, result.matchedEvents().size());
        assertNull(originator.getMatchingState());
        assertNull(originator.getReconciliationState());
        assertNull(lms.getMatchingState());
        assertNull(bank.getReconciliationState());
    }

    private CanonicalEvent event(SourceSystem source, String businessEventId, String loanId, String relationship, String amount, String currency) {
        CanonicalEvent event = new CanonicalEvent();
        event.setSourceSystem(source);
        event.setBusinessEventId(businessEventId);
        event.setImmutableSourceRecordId(businessEventId);
        event.setLoanId(loanId);
        event.setPartnerLoanReference(source == SourceSystem.BANK ? relationship : relationship);
        event.setCorrelationId(source == SourceSystem.BANK ? relationship : relationship);
        event.setParentReference(source == SourceSystem.BANK ? relationship : null);
        event.setAmount(new BigDecimal(amount));
        event.setCurrency(currency);
        event.setEventType(source == SourceSystem.ORIGINATOR
            ? "DISBURSEMENT_INSTRUCTION"
            : source == SourceSystem.LMS ? "LOAN_BOOKING" : "SETTLEMENT");
        event.setSourceStatus(source == SourceSystem.BANK ? "POSTED" : "APPROVED");
        event.setIngestionState(IngestionState.VALIDATED);
        event.setValidationState(ValidationState.VALID);
        return event;
    }

    private BigDecimal configuredTolerance() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of("config", "reconciliation.yml")));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        return new BigDecimal(properties.getProperty("reconciliation.amountToleranceInr"));
    }
}