package com.example.spring.wechat.reminder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reminder")
public record ReminderProperties(
        Scheduler scheduler,
        Delivery delivery,
        String defaultTimezone) {

    public ReminderProperties {
        scheduler = scheduler == null ? new Scheduler(15_000, 20, 300) : scheduler;
        delivery = delivery == null ? new Delivery(3, 60) : delivery;
        defaultTimezone = defaultTimezone == null || defaultTimezone.isBlank()
                ? "Asia/Shanghai"
                : defaultTimezone.strip();
    }

    public record Scheduler(int pollIntervalMs, int batchSize, int lockTimeoutSeconds) {
        public Scheduler {
            pollIntervalMs = pollIntervalMs <= 0 ? 15_000 : pollIntervalMs;
            batchSize = batchSize <= 0 ? 20 : Math.min(batchSize, 100);
            lockTimeoutSeconds = lockTimeoutSeconds <= 0 ? 300 : lockTimeoutSeconds;
        }
    }

    public record Delivery(int maxRetryCount, int retryDelaySeconds) {
        public Delivery {
            maxRetryCount = maxRetryCount <= 0 ? 3 : maxRetryCount;
            retryDelaySeconds = retryDelaySeconds <= 0 ? 60 : retryDelaySeconds;
        }
    }
}
