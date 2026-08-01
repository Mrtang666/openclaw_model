package com.example.spring.wechat.care.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "care")
public record CareProperties(
        String bootstrapKey,
        int sessionTtlHours,
        Notification notification) {

    public CareProperties {
        bootstrapKey = clean(bootstrapKey);
        sessionTtlHours = sessionTtlHours <= 0 ? 12 : Math.min(sessionTtlHours, 168);
        notification = notification == null ? new Notification(true, 15_000, 20, 3, 60, 300) : notification;
    }

    public boolean bootstrapEnabled() {
        return !bootstrapKey.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public record Notification(
            boolean enabled,
            int pollIntervalMs,
            int batchSize,
            int maxRetryCount,
            int retryDelaySeconds,
            int lockTimeoutSeconds) {

        public Notification {
            pollIntervalMs = pollIntervalMs <= 0 ? 15_000 : pollIntervalMs;
            batchSize = batchSize <= 0 ? 20 : Math.min(batchSize, 100);
            maxRetryCount = maxRetryCount <= 0 ? 3 : Math.min(maxRetryCount, 10);
            retryDelaySeconds = retryDelaySeconds <= 0 ? 60 : retryDelaySeconds;
            lockTimeoutSeconds = lockTimeoutSeconds <= 0 ? 300 : lockTimeoutSeconds;
        }
    }
}
