package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xhs.alert")
public record XhsAlertProperties(
        boolean enabled,
        int maxDeliveryAttempts,
        int batchSize) {

    public XhsAlertProperties {
        maxDeliveryAttempts = Math.max(1, maxDeliveryAttempts <= 0 ? 3 : maxDeliveryAttempts);
        batchSize = Math.max(1, Math.min(batchSize <= 0 ? 20 : batchSize, 100));
    }
}
