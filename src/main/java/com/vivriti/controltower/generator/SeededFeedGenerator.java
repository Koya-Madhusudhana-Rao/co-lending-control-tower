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
    private final Path outputDir;

    public SeededFeedGenerator(long seed, int disbursementCount, int partnerCount, int rowsPerFeed, double anomalyRate, Path outputDir) {
        this.seed = seed;
        this.disbursementCount = disbursementCount;
        this.partnerCount = partnerCount;
        this.rowsPerFeed = rowsPerFeed;
        this.anomalyRate = anomalyRate;
        this.outputDir = outputDir;
    }

    public GeneratorOutput generate() throws IOException {
        Files.createDirectories(outputDir);

        Random random = new Random(seed);
        List<String> originatorLines = new ArrayList<>();
        List<String> lmsLines = new ArrayList<>();
        List<String> bankLines = new ArrayList<>();
        List<String> groundTruthLines = new ArrayList<>();

        originatorLines.add("instructionId,loanReference,partner,instructionDateTime,amount,currency,status,batch,receivedTime");
        lmsLines.add("bookingId,internalLoanId,partnerLoanReference,bookingDateTime,bookedAmount,currency,status,batch");
        bankLines.add("transactionReference,linkedInstructionReference,valueDateTime,debitAmount,status,reversalReference,batch");
        groundTruthLines.add("instructionId,loanReference,partner,amount,currency,anomalyType,status");

        int anomalyCount = 0;
        String[] partners = {"PARA", "VIVA", "LENDX"};

        for (int i = 0; i < rowsPerFeed; i++) {
            String instructionId = "INSTR-" + String.format(Locale.US, "%06d", i + 1);
            String loanRef = "LOAN-" + String.format(Locale.US, "%05d", i + 1);
            String partner = partners[i % partnerCount];
            String status = i % 17 == 0 ? "PENDING" : "APPROVED";
            BigDecimal amount = BigDecimal.valueOf(25000 + (i % 200) * 175 + random.nextInt(4000)).setScale(2, RoundingMode.HALF_UP);

            String originatorLine = instructionId + "," + loanRef + "," + partner + ",2026-08-0" + (i % 3 + 1) + "T10:00:00," + amount + ",INR," + status + ",BATCH-001,2026-08-0" + (i % 3 + 1) + "T10:05:00";
            originatorLines.add(originatorLine);

            String bookingId = "BOOK-" + String.format(Locale.US, "%06d", i + 1);
            String lmsLine = bookingId + ",LOAN-INT-" + String.format(Locale.US, "%05d", i + 1) + "," + loanRef + ",2026-08-0" + (i % 3 + 1) + "T10:10:00," + amount + ",INR," + status + ",BATCH-001";
            lmsLines.add(lmsLine);

            String bankStatus = (i % 26 == 0) ? "REVERSED" : "POSTED";
            String reversalReference = bankStatus.equals("REVERSED") ? "REV-" + String.format(Locale.US, "%06d", i + 1) : "";
            String bankLine = "TXN-" + String.format(Locale.US, "%06d", i + 1) + "," + instructionId + ",2026-08-0" + (i % 3 + 1) + "T11:00:00," + amount + "," + bankStatus + "," + reversalReference + ",BATCH-001";
            bankLines.add(bankLine);

            if (random.nextDouble() < anomalyRate) {
                anomalyCount++;
                String anomaly = "MISSING_EVENT";
                if (i % 6 == 0) anomaly = "DUPLICATE_EVENT";
                if (i % 6 == 1) anomaly = "AMOUNT_MISMATCH";
                if (i % 6 == 2) anomaly = "STATUS_MISMATCH";
                if (i % 6 == 3) anomaly = "TIMING_DIFFERENCE";
                if (i % 6 == 4) anomaly = "COMPOSITE_MATCH";
                if (i % 6 == 5) anomaly = "MISSING_EVENT";
                groundTruthLines.add(instructionId + "," + loanRef + "," + partner + "," + amount + ",INR," + anomaly + "," + status);
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
            "partners=" + partnerCount,
            "sourceTotals=Originator:" + (originatorLines.size() - 1) + ",LMS:" + (lmsLines.size() - 1) + ",Bank:" + (bankLines.size() - 1)
        ) + System.lineSeparator();

        Files.writeString(qualityReportPath, qualityReport, StandardCharsets.UTF_8);

        return new GeneratorOutput(originatorPath, lmsPath, bankPath, groundTruthPath, qualityReportPath,
            rowsPerFeed, rowsPerFeed, rowsPerFeed, rowsPerFeed * 3, anomalyCount);
    }
}
