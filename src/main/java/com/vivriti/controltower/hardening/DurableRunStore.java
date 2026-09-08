package com.vivriti.controltower.hardening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vivriti.controltower.close.CloseHoldDecision;
import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.exceptions.AuditEntry;
import com.vivriti.controltower.exceptions.ExceptionRecord;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DurableRunStore {
    private final Path runsRoot;
    private final ObjectMapper objectMapper;

    public DurableRunStore(Path runsRoot) {
        this.runsRoot = runsRoot;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public boolean exists(String batchFingerprint) {
        return Files.isRegularFile(runDirectory(batchFingerprint).resolve("close-hold-decision.json"));
    }

    public void save(
        String batchFingerprint,
        List<CanonicalEvent> canonicalRecords,
        List<ExceptionRecord> exceptions,
        List<AuditEntry> auditEntries,
        CloseHoldDecision decision
    ) {
        Path directory = runDirectory(batchFingerprint);
        try {
            Files.createDirectories(directory);
            write(directory.resolve("canonical-records.json"), canonicalRecords);
            write(directory.resolve("exception-records.json"), exceptions);
            write(directory.resolve("audit-entries.json"), auditEntries);
            write(directory.resolve("close-hold-decision.json"), decision);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist run " + batchFingerprint, exception);
        }
    }

    public PipelineSnapshot loadSnapshot(String batchFingerprint) {
        Path directory = runDirectory(batchFingerprint);
        try {
            JsonNode canonical = objectMapper.readTree(directory.resolve("canonical-records.json").toFile());
            JsonNode exceptions = objectMapper.readTree(directory.resolve("exception-records.json").toFile());
            JsonNode audits = objectMapper.readTree(directory.resolve("audit-entries.json").toFile());
            JsonNode decision = objectMapper.readTree(directory.resolve("close-hold-decision.json").toFile());
            List<String> canonicalFingerprints = new ArrayList<>();
            for (JsonNode event : canonical) {
                String amount = text(event, "amount");
                if (!"null".equals(amount)) {
                    amount = new BigDecimal(amount).stripTrailingZeros().toPlainString();
                }
                canonicalFingerprints.add(String.join("|", text(event, "sourceSystem"), text(event, "businessEventId"),
                    amount, text(event, "payloadHash"), text(event, "matchingState"), text(event, "reconciliationState")));
            }
            List<String> exceptionIds = new ArrayList<>();
            for (JsonNode exception : exceptions) {
                exceptionIds.add(text(exception, "exceptionId"));
            }
            CloseHoldDecision closeHoldDecision = new CloseHoldDecision(
                CloseHoldDecision.Decision.valueOf(text(decision, "decision")),
                new BigDecimal(text(decision, "thresholdInr")),
                new BigDecimal(text(decision, "blockingInr")),
                objectMapper.convertValue(decision.get("blockingRecordReferences"), List.class)
            );
            return new PipelineSnapshot(batchFingerprint, canonicalFingerprints.stream().sorted().toList(),
                exceptionIds.stream().sorted().toList(), closeHoldDecision, audits.size());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load persisted run " + batchFingerprint, exception);
        }
    }

    public JsonNode loadExceptionRecords(String batchFingerprint) {
        return read(batchFingerprint, "exception-records.json");
    }

    public JsonNode loadAuditEntries(String batchFingerprint) {
        return read(batchFingerprint, "audit-entries.json");
    }

    private JsonNode read(String batchFingerprint, String filename) {
        try {
            return objectMapper.readTree(runDirectory(batchFingerprint).resolve(filename).toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + filename, exception);
        }
    }

    private void write(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private Path runDirectory(String batchFingerprint) {
        return runsRoot.resolve(batchFingerprint);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "null" : value.asText();
    }
}