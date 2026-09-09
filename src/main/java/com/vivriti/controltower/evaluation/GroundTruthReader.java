package com.vivriti.controltower.evaluation;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The ONLY code path permitted to read ground-truth files; runtime reconciliation must never depend on this. */
public class GroundTruthReader {

    public List<GroundTruthRecord> read(Path groundTruthCsv) {
        try {
            List<String> lines = Files.readAllLines(groundTruthCsv);
            List<GroundTruthRecord> records = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                String[] columns = lines.get(index).split(",", -1);
                records.add(new GroundTruthRecord(columns[0], new BigDecimal(columns[3]), columns[4], columns[5], columns[6]));
            }
            return List.copyOf(records);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read ground truth " + groundTruthCsv, exception);
        }
    }
}
