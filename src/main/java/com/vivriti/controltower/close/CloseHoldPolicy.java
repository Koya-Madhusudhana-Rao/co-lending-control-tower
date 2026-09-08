package com.vivriti.controltower.close;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Properties;

public record CloseHoldPolicy(BigDecimal absoluteThresholdInr, BigDecimal relativeThresholdRate) {
    public static CloseHoldPolicy fromConfig(Path configPath) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(configPath));
        yaml.afterPropertiesSet();
        Properties properties = yaml.getObject();
        if (properties == null) {
            throw new IllegalArgumentException("Close policy config is required");
        }
        return new CloseHoldPolicy(
            new BigDecimal(properties.getProperty("reconciliation.closePolicy.absoluteThresholdInr")),
            new BigDecimal(properties.getProperty("reconciliation.closePolicy.relativeThresholdRate"))
        );
    }

    public BigDecimal thresholdFor(BigDecimal batchTotalInr) {
        return absoluteThresholdInr.min(relativeThresholdRate.multiply(batchTotalInr));
    }
}