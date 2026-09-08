package com.vivriti.controltower.matching;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.domain.ValidationState;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompositeAndTimingMatcher {

    private static final Duration GRACE_WINDOW = Duration.ofHours(2);

    public CompositeAndTimingResult reconcile(List<CanonicalEvent> events) {
        List<CanonicalEvent> leftoverUnmatched = events.stream()
            .filter(event -> event.getMatchingState() != MatchingState.EXACT_MATCH)
            .filter(event -> event.getReconciliationState() != ReconciliationState.MATCHED)
            .toList();
        List<CanonicalEvent> timingEvents = applyTimingState(leftoverUnmatched);
        Set<CanonicalEvent> timingSet = new HashSet<>(timingEvents);
        CompositeMatches compositeMatches = applyCompositeState(leftoverUnmatched.stream()
            .filter(event -> !timingSet.contains(event))
            .toList());
        return new CompositeAndTimingResult(compositeMatches.groups(), timingEvents, compositeMatches.events());
    }

    private List<CanonicalEvent> applyTimingState(List<CanonicalEvent> events) {
        List<CanonicalEvent> timingEvents = new ArrayList<>();
        for (CanonicalEvent event : events) {
            if (event.getValidationState() != ValidationState.LATE_ARRIVAL
                || event.getReceivedTimestamp() == null
                || event.getReconciliationCutOff() == null) {
                continue;
            }
            LocalDateTime cutoff = event.getReconciliationCutOff();
            if (event.getReceivedTimestamp().isAfter(cutoff)
                && !event.getReceivedTimestamp().isAfter(cutoff.plus(GRACE_WINDOW))) {
                event.setMatchingState(MatchingState.TIMING_DIFFERENCE_PENDING);
                event.setReconciliationState(ReconciliationState.PENDING);
                timingEvents.add(event);
            }
        }
        return timingEvents;
    }

    private CompositeMatches applyCompositeState(List<CanonicalEvent> events) {
        Map<CompositeKey, List<CanonicalEvent>> originators = events.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.ORIGINATOR)
            .filter(event -> "DISBURSEMENT_INSTRUCTION".equals(event.getEventType()))
            .filter(event -> event.getPartnerLoanReference() != null)
            .collect(Collectors.groupingBy(event -> CompositeKey.from(event, event.getPartnerLoanReference())));
        Map<CompositeKey, List<CanonicalEvent>> lmsBookings = events.stream()
            .filter(event -> event.getSourceSystem() == SourceSystem.LMS)
            .filter(event -> "LOAN_BOOKING".equals(event.getEventType()))
            .filter(event -> event.getPartnerLoanReference() != null)
            .collect(Collectors.groupingBy(event -> CompositeKey.from(event, event.getPartnerLoanReference())));
        Set<CanonicalEvent> consumed = new HashSet<>();
        List<CanonicalEvent> matched = new ArrayList<>();
        int groupCount = 0;

        for (Map.Entry<CompositeKey, List<CanonicalEvent>> entry : originators.entrySet()) {
            List<CanonicalEvent> originatorGroup = entry.getValue();
            List<CanonicalEvent> lmsGroup = lmsBookings.getOrDefault(entry.getKey(), List.of());
            if (!isOneToManyOrManyToOne(originatorGroup, lmsGroup)
                || !balancesExactly(originatorGroup, lmsGroup)
                || overlapsConsumed(originatorGroup, lmsGroup, consumed)) {
                continue;
            }
            List<CanonicalEvent> group = new ArrayList<>(originatorGroup);
            group.addAll(lmsGroup);
            group.forEach(event -> {
                event.setMatchingState(MatchingState.COMPOSITE_MATCH);
                event.setReconciliationState(ReconciliationState.MATCHED);
                consumed.add(event);
            });
            matched.addAll(group);
            groupCount++;
        }
        return new CompositeMatches(groupCount, matched);
    }

    private boolean isOneToManyOrManyToOne(List<CanonicalEvent> originators, List<CanonicalEvent> lmsBookings) {
        return !originators.isEmpty() && !lmsBookings.isEmpty()
            && ((originators.size() == 1 && lmsBookings.size() > 1)
                || (originators.size() > 1 && lmsBookings.size() == 1));
    }

    private boolean balancesExactly(List<CanonicalEvent> originators, List<CanonicalEvent> lmsBookings) {
        return sum(originators).compareTo(sum(lmsBookings)) == 0;
    }

    private BigDecimal sum(List<CanonicalEvent> events) {
        return events.stream().map(CanonicalEvent::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean overlapsConsumed(List<CanonicalEvent> originators, List<CanonicalEvent> lmsBookings, Set<CanonicalEvent> consumed) {
        return originators.stream().anyMatch(consumed::contains) || lmsBookings.stream().anyMatch(consumed::contains);
    }

    private record CompositeKey(String relationship, String currency) {
        private static CompositeKey from(CanonicalEvent event, String relationship) {
            return new CompositeKey(relationship, event.getCurrency());
        }
    }

    private record CompositeMatches(int groups, List<CanonicalEvent> events) {
    }
}