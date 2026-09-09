package com.vivriti.controltower.demo;

import com.vivriti.controltower.close.CloseHoldDecision;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Explicit opt-in demo entry point; active only under the {@code demo} profile so normal startup and tests never trigger it. */
@Component
@Profile("demo")
public class DemoRunner implements CommandLineRunner {

    private static final long DEFAULT_SEED = 12345L;

    private final ConfigurableApplicationContext context;

    public DemoRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        long seed = parseSeed(args);
        Path feedsRoot = Files.createDirectories(Path.of("data", "demo"));
        Path runsRoot = Path.of("data", "runs");

        PipelineDemo.DemoOutput output = new PipelineDemo().run(seed, feedsRoot, runsRoot);
        String report = renderReport(seed, output);
        System.out.println(report);
        Files.writeString(feedsRoot.resolve("demo-summary.txt"), report, StandardCharsets.UTF_8);

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private String renderReport(long seed, PipelineDemo.DemoOutput output) {
        CloseHoldDecision decision = output.featuredDecision();
        List<String> refs = decision.blockingRecordReferences();
        StringBuilder builder = new StringBuilder();
        builder.append("=== Co-lending Control Tower - Phase 1 demonstration ===\n");
        builder.append("Featured seed: ").append(seed).append('\n');
        builder.append("Durable run directory: ").append(Path.of("data", "runs", output.featuredFingerprint())).append('\n');
        builder.append('\n');
        builder.append("Full-pipeline close/hold decision (generate -> ingest -> normalize -> reconcile -> materialize -> close/hold -> persist):\n");
        builder.append("  decision            = ").append(decision.decision()).append('\n');
        builder.append("  thresholdInr        = ").append(decision.thresholdInr().toPlainString()).append('\n');
        builder.append("  blockingInr         = ").append(decision.blockingInr().toPlainString()).append('\n');
        builder.append("  blockingRefs        = ").append(refs.size()).append('\n');
        builder.append("  sampleBlockingRefs  = ").append(refs.stream().limit(3).toList()).append('\n');
        builder.append("  exceptionsPersisted = ").append(output.featuredExceptionCount()).append('\n');
        builder.append('\n');
        builder.append("Seed A vs Seed B scorecard comparison (ground truth used only for scoring):\n");
        builder.append(output.renderedReport()).append('\n');
        builder.append("=== Demonstration complete ===");
        return builder.toString();
    }

    private long parseSeed(String... args) {
        for (int index = 0; index < args.length - 1; index++) {
            if ("--seed".equals(args[index])) {
                return Long.parseLong(args[index + 1].trim());
            }
        }
        return DEFAULT_SEED;
    }
}
