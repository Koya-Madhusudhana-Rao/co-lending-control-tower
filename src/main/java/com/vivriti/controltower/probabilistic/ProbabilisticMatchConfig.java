package com.vivriti.controltower.probabilistic;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Properties;

/** Config binding for the Level 4 probabilistic scorer, mirroring the CloseHoldPolicy.fromConfig pattern. */
public record ProbabilisticMatchConfig(
    boolean enabled,
    double referenceWeight,
    double amountWeight,
    double timestampWeight,
    double partnerWeight,
    BigDecimal amountBandInr,
    double timeBandHours,
    double surfaceThreshold,
    double confirmationThreshold
) {
    public static ProbabilisticMatchConfig fromConfig(Path configPath) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(configPath));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        if (properties == null) {
            throw new IllegalArgumentException("Probabilistic config is required");
        }
        return new ProbabilisticMatchConfig(
            Boolean.parseBoolean(get(properties, "reconciliation.probabilistic.enabled")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.weights.reference")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.weights.amount")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.weights.timestamp")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.weights.partner")),
            new BigDecimal(get(properties, "reconciliation.probabilistic.amountBandInr")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.timeBandHours")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.surfaceThreshold")),
            Double.parseDouble(get(properties, "reconciliation.probabilistic.confirmationThreshold"))
        );
    }

    public double totalWeight() {
        return referenceWeight + amountWeight + timestampWeight + partnerWeight;
    }

    private static String get(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config value: " + key);
        }
        return value;
    }
}
