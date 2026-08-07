package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "xhs.image-analysis")
public record XhsImageAnalysisProperties(
        boolean enabled,
        String version,
        int batchSize,
        int maxAttempts,
        Duration claimTimeout) {

    public XhsImageAnalysisProperties {
        version = version == null || version.isBlank() ? "xhs-image-v1" : version.strip();
        batchSize = Math.max(1, Math.min(batchSize <= 0 ? 5 : batchSize, 20));
        maxAttempts = Math.max(1, Math.min(maxAttempts <= 0 ? 3 : maxAttempts, 10));
        claimTimeout = claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                ? Duration.ofMinutes(15) : claimTimeout;
    }
}
