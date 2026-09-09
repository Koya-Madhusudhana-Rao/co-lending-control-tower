package com.vivriti.controltower.evaluation;

import java.util.ArrayList;
import java.util.List;

/** Builds a side-by-side Seed A vs Seed B scorecard and an explicit written comparison; seeds are never merged. */
public class EvaluationReporter {

    private static final double RATE_EPSILON = 0.0001;

    public ScorecardComparison compare(SeedScorecard seedA, SeedScorecard seedB) {
        List<String> differences = new ArrayList<>();
        addIntDifference(differences, "exactMatchCount", seedA.exactMatchCount(), seedB.exactMatchCount());
        addIntDifference(differences, "compositeMatchCount", seedA.compositeMatchCount(), seedB.compositeMatchCount());
        addIntDifference(differences, "falseMatchCount", seedA.falseMatchCount(), seedB.falseMatchCount());
        addRateDifference(differences, "straightThroughRate", seedA.straightThroughRate(), seedB.straightThroughRate());
        addRateDifference(differences, "exceptionCoverageRate", seedA.exceptionCoverageRate(), seedB.exceptionCoverageRate());
        if (seedA.controlTotalIntegrity() != seedB.controlTotalIntegrity()) {
            differences.add("controlTotalIntegrity differs: A=" + seedA.controlTotalIntegrity()
                + " B=" + seedB.controlTotalIntegrity());
        }
        return new ScorecardComparison(seedA, seedB, differences);
    }

    public String render(ScorecardComparison comparison) {
        SeedScorecard a = comparison.seedA();
        SeedScorecard b = comparison.seedB();
        StringBuilder builder = new StringBuilder();
        builder.append("Metric | ").append(a.seedLabel()).append(" | ").append(b.seedLabel()).append('\n');
        builder.append(row("exactMatch", a.exactMatchCount() + " (INR " + a.exactMatchInr().toPlainString() + ")",
            b.exactMatchCount() + " (INR " + b.exactMatchInr().toPlainString() + ")"));
        builder.append(row("compositeMatch", a.compositeMatchCount() + " (INR " + a.compositeMatchInr().toPlainString() + ")",
            b.compositeMatchCount() + " (INR " + b.compositeMatchInr().toPlainString() + ")"));
        builder.append(row("falseMatch", a.falseMatchCount() + " (INR " + a.falseMatchInr().toPlainString() + ")",
            b.falseMatchCount() + " (INR " + b.falseMatchInr().toPlainString() + ")"));
        builder.append(row("exceptionCoverage", format(a.exceptionCoverageRate()), format(b.exceptionCoverageRate())));
        builder.append(row("straightThrough", format(a.straightThroughRate()), format(b.straightThroughRate())));
        builder.append(row("controlTotalIntegrity", String.valueOf(a.controlTotalIntegrity()),
            String.valueOf(b.controlTotalIntegrity())));
        builder.append("Unresolved (").append(a.seedLabel()).append("): ").append(a.unresolvedReferences()).append('\n');
        builder.append("Unresolved (").append(b.seedLabel()).append("): ").append(b.unresolvedReferences()).append('\n');
        builder.append("Failed cases (").append(a.seedLabel()).append("): ").append(a.falseMatchReferences()).append('\n');
        builder.append("Failed cases (").append(b.seedLabel()).append("): ").append(b.falseMatchReferences()).append('\n');
        builder.append("Material differences: ");
        builder.append(comparison.materialDifferences().isEmpty()
            ? "none" : String.join("; ", comparison.materialDifferences()));
        return builder.toString();
    }

    private void addIntDifference(List<String> differences, String name, int a, int b) {
        if (a != b) {
            differences.add(name + " differs: A=" + a + " B=" + b);
        }
    }

    private void addRateDifference(List<String> differences, String name, double a, double b) {
        if (Math.abs(a - b) > RATE_EPSILON) {
            differences.add(name + " differs: A=" + format(a) + " B=" + format(b));
        }
    }

    private String row(String metric, String a, String b) {
        return metric + " | " + a + " | " + b + '\n';
    }

    private String format(double rate) {
        return String.format(java.util.Locale.US, "%.4f", rate);
    }
}
