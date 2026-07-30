package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "xhs.scheduled-report")
public record XhsScheduledReportProperties(
        boolean enabled,
        String storageDir,
        Duration pollingDelay,
        Duration collectionWait,
        Duration analysisWait,
        int maxDeliveryAttempts,
        int retentionDays) {

    public XhsScheduledReportProperties {
        storageDir = storageDir == null || storageDir.isBlank() ? "data/xhs/reports" : storageDir.strip();
        pollingDelay = pollingDelay == null || pollingDelay.isNegative() || pollingDelay.isZero()
                ? Duration.ofSeconds(10) : pollingDelay;
        collectionWait = collectionWait == null || collectionWait.isNegative() || collectionWait.isZero()
                ? Duration.ofMinutes(15) : collectionWait;
        analysisWait = analysisWait == null || analysisWait.isNegative() || analysisWait.isZero()
                ? Duration.ofMinutes(10) : analysisWait;
        maxDeliveryAttempts = Math.max(1, Math.min(maxDeliveryAttempts <= 0 ? 3 : maxDeliveryAttempts, 10));
        retentionDays = Math.max(1, retentionDays <= 0 ? 30 : retentionDays);
    }
}
