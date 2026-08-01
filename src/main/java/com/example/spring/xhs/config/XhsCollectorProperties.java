package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "xhs.collector")
public record XhsCollectorProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration timeout,
        Duration pollingDelay,
        int maxAttempts) {

    public XhsCollectorProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        apiKey = apiKey == null ? "" : apiKey.strip();
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(20) : timeout;
        pollingDelay = pollingDelay == null || pollingDelay.isNegative() || pollingDelay.isZero()
                ? Duration.ofSeconds(10)
                : pollingDelay;
        maxAttempts = Math.max(1, maxAttempts <= 0 ? 30 : maxAttempts);
    }
}
