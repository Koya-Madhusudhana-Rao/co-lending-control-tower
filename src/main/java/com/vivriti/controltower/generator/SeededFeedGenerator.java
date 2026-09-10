package com.vivriti.controltower.generator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SeededFeedGenerator {

    private final long seed;
    private final int disbursementCount;
    private final int partnerCount;
    private final int rowsPerFeed;
    private final double anomalyRate;
    private final double referenceMismatchRate;
    private final Path outputDir;

    public SeededFeedGenerator(long seed, int disbursementCount, int partnerCount, int rowsPerFeed, double anomalyRate, Path outputDir) {
        this(seed, disbursementCount, partnerCount, rowsPerFeed, anomalyRate, 0.0d, outputDir);
    }

    public SeededFeedGenerator(long seed, int disbursementCount, int partnerCount, int rowsPerFeed, double anomalyRate, double referenceMismatchRate, Path outputDir) {
        this.seed = seed;
        this.disbursementCount = disbursementCount;
        this.partnerCount = partnerCount;
        this.rowsPerFeed = rowsPerFeed;
        this.anomalyRate = anomalyRate;
        this.referenceMismatchRate = referenceMismatchRate;
        this.outputDir = outputDir;
    }

    public GeneratorOutput generate() throws IOException {
        Files.createDirectories(outputDir);

        Random random = new Random(seed);
        // Separate stream so the additive reference-mismatch class never perturbs the Phase 1 anomaly/amount stream.
        Random referenceMismatchRandom = new Random(seed * 31 + 17);
        List<String> originatorLines = new ArrayList<>();
        List<String> lmsLines = new ArrayList<>();
        List<String> bankLines = new ArrayList<>();
        List<String> groundTruthLines = new ArrayList<>();

        originatorLines.add("instructionId,loanReference,partner,instructionDateTime,amount,currency,status,batch,receivedTime");
        lmsLines.add("bookingId,internalLoanId,partnerLoanReference,bookingDateTime,bookedAmount,currency,status,batch");
        bankLines.add("transactionReference,linkedInstructionReference,valueDateTime,debitAmount,status,reversalReference,batch");
        groundTruthLines.add("instructionId,loanReference,partner,amount,currency,anomalyType,status,affectedSource,action");

        int anomalyCount = 0;
        int referenceMismatchCount = 0;
        List<String> corruptedReferences = new ArrayList<>();
        java.util.Set<String> realLinkingReferences = new java.util.HashSet<>();
        String[] partners = {"PARA", "VIVA", "LENDX"};

        for (int i = 0; i < rowsPerFeed; i++) {
            String instructionId = "INSTR-" + String.format(Locale.US, "%06d", i + 1);
            String loanRef = "LOAN-" + String.format(Locale.US, "%05d", i + 1);
            realLinkingReferences.add(instructionId);
            realLinkingReferences.add(loanRef);
            String partner = partners[i % partnerCount];
            String status = i % 17 == 0 ? "PENDING" : "APPROVED";
            BigDecimal amount = BigDecimal.valueOf(25000 + (i % 200) * 175 + random.nextInt(4000)).setScale(2, RoundingMode.HALF_UP);

            String bookingId = "BOOK-" + String.format(Locale.US, "%06d", i + 1);
            String bankStatus = (i % 26 == 0) ? "REVERSED" : "POSTED";
            String reversalReference = bankStatus.equals("REVERSED") ? "REV-" + String.format(Locale.US, "%06d", i + 1) : "";
            String businessDay = "2026-08-0" + (i % 3 + 1);
            String anomaly = null;

            if (random.nextDouble() < anomalyRate) {
                anomalyCount++;
                anomaly = "MISSING_EVENT";
                if (i % 6 == 0) anomaly = "DUPLICATE_EVENT";
                if (i % 6 == 1) anomaly = "AMOUNT_MISMATCH";
                if (i % 6 == 2) anomaly = "STATUS_MISMATCH";
                if (i % 6 == 3) anomaly = "TIMING_DIFFERENCE";
                if (i % 6 == 4) anomaly = "COMPOSITE_MATCH";
                if (i % 6 == 5) anomaly = "MISSING_EVENT";
            }

            // Additive Phase 2 class: only otherwise-clean, would-be-exact-match records get their linking references corrupted.
            boolean referenceMismatch = referenceMismatchRate > 0.0
                && anomaly == null
                && "POSTED".equals(bankStatus)
                && referenceMismatchRandom.nextDouble() < referenceMismatchRate;

            String originatorReceivedTime = "TIMING_DIFFERENCE".equals(anomaly) ? "2026-08-04T18:30:00" : businessDay + "T10:05:00";
            String originatorLine = instructionId + "," + loanRef + "," + partner + "," + businessDay + "T10:00:00," + amount + ",INR," + status + ",BATCH-001," + originatorReceivedTime;
            originatorLines.add(originatorLine);
            if ("DUPLICATE_EVENT".equals(anomaly)) {
                originatorLines.add(originatorLine);
            }

            BigDecimal lmsAmount = "AMOUNT_MISMATCH".equals(anomaly) ? amount.add(new BigDecimal("2.00")) : amount;
            String lmsStatus = "STATUS_MISMATCH".equals(anomaly) ? "BOOKING_REVIEW" : status;
            if ("COMPOSITE_MATCH".equals(anomaly)) {
                BigDecimal firstPart = amount.divide(new BigDecimal("2"), 2, RoundingMode.DOWN);
                BigDecimal secondPart = amount.subtract(firstPart).setScale(2, RoundingMode.HALF_UP);
                lmsLines.add(bookingId + "-A,LOAN-INT-" + String.format(Locale.US, "%05d", i + 1) + "-A," + loanRef + "," + businessDay + "T10:10:00," + firstPart + ",INR," + status + ",BATCH-001");
                lmsLines.add(bookingId + "-B,LOAN-INT-" + String.format(Locale.US, "%05d", i + 1) + "-B," + loanRef + "," + businessDay + "T10:12:00," + secondPart + ",INR," + status + ",BATCH-001");
                bankLines.add("TXN-" + String.format(Locale.US, "%06d", i + 1) + "-A," + instructionId + "," + businessDay + "T11:00:00," + firstPart + "," + bankStatus + "," + reversalReference + ",BATCH-001");
                bankLines.add("TXN-" + String.format(Locale.US, "%06d", i + 1) + "-B," + instructionId + "," + businessDay + "T11:02:00," + secondPart + "," + bankStatus + "," + reversalReference + ",BATCH-001");
            } else {
                String lmsPartnerReference = referenceMismatch ? corruptReference(loanRef) : loanRef;
                String bankLinkedReference = referenceMismatch ? corruptReference(instructionId) : instructionId;
                lmsLines.add(bookingId + ",LOAN-INT-" + String.format(Locale.US, "%05d", i + 1) + "," + lmsPartnerReference + "," + businessDay + "T10:10:00," + lmsAmount + ",INR," + lmsStatus + ",BATCH-001");
                if (!"MISSING_EVENT".equals(anomaly)) {
                    // A reversal settlement is represented as a signed (negative) debit amount, preserved as-is.
                    BigDecimal bankDebitAmount = "REVERSED".equals(bankStatus) ? amount.negate() : amount;
                    bankLines.add("TXN-" + String.format(Locale.US, "%06d", i + 1) + "," + bankLinkedReference + "," + businessDay + "T11:00:00," + bankDebitAmount + "," + bankStatus + "," + reversalReference + ",BATCH-001");
                }
                if (referenceMismatch) {
                    corruptedReferences.add(lmsPartnerReference);
                    corruptedReferences.add(bankLinkedReference);
                }
            }

            if (anomaly != null) {
                groundTruthLines.add(instructionId + "," + loanRef + "," + partner + "," + amount + ",INR," + anomaly + "," + status + "," + affectedSource(anomaly) + "," + action(anomaly));
            }
            if (referenceMismatch) {
                referenceMismatchCount++;
                groundTruthLines.add(instructionId + "," + loanRef + "," + partner + "," + amount + ",INR,REFERENCE_MISMATCH," + status + "," + affectedSource("REFERENCE_MISMATCH") + "," + action("REFERENCE_MISMATCH"));
            }
        }

        for (String corrupted : corruptedReferences) {
            if (realLinkingReferences.contains(corrupted)) {
                throw new IllegalStateException("Corrupted reference collides with a real linking reference: " + corrupted);
            }
        }

        Path originatorPath = outputDir.resolve("originator.csv");
        Path lmsPath = outputDir.resolve("lms.csv");
        Path bankPath = outputDir.resolve("bank.csv");
        Path groundTruthPath = outputDir.resolve("ground-truth.csv");
        Path qualityReportPath = outputDir.resolve("data-quality-report.txt");

        Files.writeString(originatorPath, String.join(System.lineSeparator(), originatorLines) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(lmsPath, String.join(System.lineSeparator(), lmsLines) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(bankPath, String.join(System.lineSeparator(), bankLines) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(groundTruthPath, String.join(System.lineSeparator(), groundTruthLines) + System.lineSeparator(), StandardCharsets.UTF_8);

        String qualityReport = String.join(System.lineSeparator(),
            "seed=" + seed,
            "generatorVersion=1.0.0",
            "disbursementCount=" + disbursementCount,
            "originatorRows=" + originatorLines.size() + " (including header)",
            "lmsRows=" + lmsLines.size() + " (including header)",
            "bankRows=" + bankLines.size() + " (including header)",
            "totalRows=" + (originatorLines.size() + lmsLines.size() + bankLines.size() - 3),
            "uniqueBusinessEvents=" + rowsPerFeed,
            "anomalyCount=" + anomalyCount,
            "referenceMismatchCount=" + referenceMismatchCount,
            "partners=" + partnerCount,
            "sourceTotals=Originator:" + (originatorLines.size() - 1) + ",LMS:" + (lmsLines.size() - 1) + ",Bank:" + (bankLines.size() - 1)
        ) + System.lineSeparator();

        Files.writeString(qualityReportPath, qualityReport, StandardCharsets.UTF_8);

        return new GeneratorOutput(originatorPath, lmsPath, bankPath, groundTruthPath, qualityReportPath,
            originatorLines.size() - 1, lmsLines.size() - 1, bankLines.size() - 1,
            originatorLines.size() + lmsLines.size() + bankLines.size() - 3, anomalyCount, referenceMismatchCount);
    }

    private String affectedSource(String anomaly) {
        return switch (anomaly) {
            case "MISSING_EVENT" -> "BANK";
            case "DUPLICATE_EVENT", "TIMING_DIFFERENCE" -> "ORIGINATOR";
            case "AMOUNT_MISMATCH", "STATUS_MISMATCH" -> "LMS";
            case "COMPOSITE_MATCH", "REFERENCE_MISMATCH" -> "LMS+BANK";
            default -> "UNKNOWN";
        };
    }

    private String action(String anomaly) {
        return switch (anomaly) {
            case "MISSING_EVENT" -> "omitted bank settlement row";
            case "DUPLICATE_EVENT" -> "duplicated originator row";
            case "AMOUNT_MISMATCH" -> "increased LMS amount by INR 2.00";
            case "STATUS_MISMATCH" -> "changed LMS status";
            case "TIMING_DIFFERENCE" -> "moved originator receivedTime after batch cutoff inside grace window";
            case "COMPOSITE_MATCH" -> "split LMS and bank rows into two exact-sum components";
            case "REFERENCE_MISMATCH" -> "corrupted LMS partnerLoanReference and bank linkedInstructionReference; amount and timestamp preserved";
            default -> "none";
        };
    }

    private String corruptReference(String reference) {
        int dash = reference.indexOf('-');
        // Replace the first digit after the prefix with a non-numeric marker: breaks exact equality, keeps high similarity, cannot collide with any numeric reference.
        return reference.substring(0, dash + 1) + "X" + reference.substring(dash + 2);
    }
}
