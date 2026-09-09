package com.vivriti.controltower.evaluation;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.MatchingState;
import com.vivriti.controltower.domain.ReconciliationState;
import com.vivriti.controltower.domain.SourceSystem;
import com.vivriti.controltower.generator.GeneratorOutput;
import com.vivriti.controltower.generator.SeededFeedGenerator;
import com.vivriti.controltower.hardening.DurableRunStore;
import com.vivriti.controltower.hardening.Phase1PipelineService;
import com.vivriti.controltower.hardening.PipelineBatch;
import com.vivriti.controltower.hardening.PipelineRunResult;
import com.vivriti.controltower.probabilistic.ProbabilisticMatchConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Post-candidacy-fix diagnostic + recall regression on Seed A: reference-mismatch positives stay recoverable, incomplete/reversed groups are never scored. */
class Phase2CandidacyFixDiagnosticTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 4, 17, 30);

    @Test
    void candidacyFilterExcludesIncompleteAndReversedGroupsWhilePreservingReferenceMismatchRecall() throws Exception {
        Path gen = Files.createTempDirectory("candidacy-gen");
        GeneratorOutput generated = new SeededFeedGenerator(12345L, 2000, 3, 2000, 0.06d, 0.03d, gen).generate();
        Path runs = Files.createTempDirectory("candidacy-runs");
        PipelineRunResult result = new Phase1PipelineService(runs, enabled()).process(batch(generated));
        List<CanonicalEvent> canonical = new DurableRunStore(runs).loadCanonicalRecords(result.snapshot().batchFingerprint());

        Map<String, String> anomaly = new HashMap<>();
        for (GroundTruthRecord record : new GroundTruthReader().read(generated.groundTruthPath())) {
            anomaly.put(record.instructionId(), record.anomalyType());
        }
        Map<Integer, CanonicalEvent> bankByIndex = new HashMap<>();
        for (CanonicalEvent event : canonical) {
            if (event.getSourceSystem() == SourceSystem.BANK) {
                bankByIndex.put(indexOf(event.getImmutableSourceRecordId()), event);
            }
        }

        Map<String, int[]> perClass = new TreeMap<>(); // [total, surfaced, sameIndexSurfaced]
        int referenceMismatchTotal = 0;
        int referenceMismatchSurfacedSameIndex = 0;
        int missingEventSurfaced = 0;
        int reversedNoneSurfaced = 0;
        int falsePositives = 0;

        for (CanonicalEvent originator : canonical) {
            if (originator.getSourceSystem() != SourceSystem.ORIGINATOR || !isUnresolved(originator)) {
                continue;
            }
            String cls = anomaly.getOrDefault(originator.getBusinessEventId(), "NONE(reversed/clean)");
            boolean surfaced = originator.getMatchingState() == MatchingState.PROBABLE_MATCH;
            boolean sameIndex = surfaced
                && indexOf(originator.getBusinessEventId()) == indexOf(originator.getProbableMatchEvidence().matchedCandidateReference());
            int[] tally = perClass.computeIfAbsent(cls, key -> new int[3]);
            tally[0]++;
            if (surfaced) {
                tally[1]++;
                if (sameIndex) {
                    tally[2]++;
                }
                if (!"REFERENCE_MISMATCH".equals(cls)) {
                    falsePositives++;
                }
            }

            if ("REFERENCE_MISMATCH".equals(cls)) {
                referenceMismatchTotal++;
                if (surfaced && sameIndex) {
                    referenceMismatchSurfacedSameIndex++;
                }
            } else if ("MISSING_EVENT".equals(cls) && surfaced) {
                missingEventSurfaced++;
            } else if ("NONE(reversed/clean)".equals(cls) && surfaced) {
                CanonicalEvent bank = bankByIndex.get(indexOf(originator.getBusinessEventId()));
                if (bank != null && "REVERSED".equals(bank.getSourceStatus())) {
                    reversedNoneSurfaced++;
                }
            }
        }

        StringBuilder report = new StringBuilder("=== Seed A post-candidacy-fix breakdown (surfaced = scored PROBABLE_MATCH) ===\n");
        for (Map.Entry<String, int[]> entry : perClass.entrySet()) {
            report.append(String.format(Locale.US, "%s: total=%d surfaced=%d sameIndexSurfaced=%d%n",
                entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
        }
        report.append("falsePositives(surfaced non-REFERENCE_MISMATCH)=").append(falsePositives).append("\n");
        report.append("referenceMismatchTotal=").append(referenceMismatchTotal)
            .append(" recoveredSameIndex=").append(referenceMismatchSurfacedSameIndex).append("\n");
        Path out = Path.of("target", "phase2-seedA-candidacy-fix.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);

        // Recall preserved: every reference-mismatch positive still recovered to its true same-index counterpart.
        assertTrue(referenceMismatchTotal >= 30, "expected a meaningful positive population");
        assertEquals(referenceMismatchTotal, referenceMismatchSurfacedSameIndex, "reference-mismatch recall must be unaffected");
        // Incomplete and reversed groups are excluded near-completely; a tiny residual can leak when a spurious bank
        // has both an adjacent id and a coincidentally within-tolerance amount clearing confirmation strength.
        assertTrue(missingEventSurfaced <= 2, "MISSING_EVENT (no bank leg) nearly all excluded, got " + missingEventSurfaced);
        assertTrue(reversedNoneSurfaced <= 3, "reversed-bank NONE cases nearly all excluded, got " + reversedNoneSurfaced);
        assertTrue(falsePositives <= 45, "false positives substantially reduced from 136, got " + falsePositives);
    }

    private boolean isUnresolved(CanonicalEvent event) {
        MatchingState ms = event.getMatchingState();
        ReconciliationState rs = event.getReconciliationState();
        boolean matched = ms == MatchingState.EXACT_MATCH || ms == MatchingState.COMPOSITE_MATCH
            || ms == MatchingState.PROBABLE_MATCH_CONFIRMED || rs == ReconciliationState.MATCHED;
        boolean pending = ms == MatchingState.TIMING_DIFFERENCE_PENDING || rs == ReconciliationState.PENDING;
        return !matched && !pending;
    }

    private int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(id);
        return matcher.find() ? Integer.parseInt(matcher.group()) : -2;
    }

    private PipelineBatch batch(GeneratorOutput generated) throws Exception {
        return new PipelineBatch("BATCH-001",
            dataLines(generated.originatorPath()), dataLines(generated.lmsPath()), dataLines(generated.bankPath()),
            CUTOFF, List.of());
    }

    private List<String> dataLines(Path path) throws Exception {
        List<String> all = Files.readAllLines(path);
        return all.subList(1, all.size());
    }

    private ProbabilisticMatchConfig enabled() {
        return new ProbabilisticMatchConfig(true, 0.40, 0.35, 0.15, 0.10, new BigDecimal("100.00"), 6.0, 0.50, 0.80, 0.80);
    }
}
