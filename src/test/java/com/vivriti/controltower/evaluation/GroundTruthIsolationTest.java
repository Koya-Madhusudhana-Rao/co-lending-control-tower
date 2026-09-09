package com.vivriti.controltower.evaluation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Guards the hard rule: only the evaluation package may read ground truth; runtime reconciliation must not depend on it. */
class GroundTruthIsolationTest {

    private static final Path RUNTIME_ROOT = Path.of("src", "main", "java", "com", "vivriti", "controltower");
    private static final List<String> RUNTIME_PACKAGES = List.of(
        "ingestion", "normalization", "matching", "exceptions", "close", "hardening", "domain");

    @Test
    void runtimeReconciliationPackagesDoNotImportEvaluationOrGroundTruthReader() throws IOException {
        for (String runtimePackage : RUNTIME_PACKAGES) {
            Path packageDir = RUNTIME_ROOT.resolve(runtimePackage);
            if (!Files.isDirectory(packageDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(packageDir)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGroundTruthDependency);
            }
        }
    }

    @Test
    void groundTruthReaderLivesOnlyInTheEvaluationPackage() {
        assertTrue(Files.isRegularFile(RUNTIME_ROOT.resolve("evaluation").resolve("GroundTruthReader.java")));
        assertTrue(Files.notExists(RUNTIME_ROOT.resolve("matching").resolve("GroundTruthReader.java")));
        assertTrue(Files.notExists(RUNTIME_ROOT.resolve("hardening").resolve("GroundTruthReader.java")));
    }

    private void assertNoGroundTruthDependency(Path javaFile) {
        try {
            String source = Files.readString(javaFile);
            if (source.contains("com.vivriti.controltower.evaluation")) {
                fail("Runtime file leaks evaluation dependency: " + javaFile);
            }
            if (source.contains("GroundTruthReader") || source.contains("GroundTruthRecord")) {
                fail("Runtime file references ground-truth reader types: " + javaFile);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + javaFile, exception);
        }
    }
}
