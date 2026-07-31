package com.example.spring.wechat.care.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "care.task")
public record CareTaskProperties(
        boolean enabled,
        int pollIntervalMs,
        int batchSize,
        int generationHorizonDays,
        int maxPostponeMinutes) {

    public CareTaskProperties {
        pollIntervalMs = pollIntervalMs <= 0 ? 15_000 : pollIntervalMs;
        batchSize = batchSize <= 0 ? 100 : Math.min(batchSize, 500);
        generationHorizonDays = generationHorizonDays < 0 ? 1 : Math.min(generationHorizonDays, 7);
        maxPostponeMinutes = maxPostponeMinutes <= 0 ? 1_440 : Math.min(maxPostponeMinutes, 10_080);
    }
}
