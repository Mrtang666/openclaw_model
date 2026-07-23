package com.example.spring.wechat.bot.concurrency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "wechat.concurrency")
public class WechatConcurrencyProperties {

    private int workerThreads = 8;
    private int modelMaxConcurrency = 6;
    private int userQueueCapacity = 5;
    private int globalQueueCapacity = 100;
    private Duration taskTimeout = Duration.ofSeconds(180);
    private Duration userMailboxIdle = Duration.ofMinutes(10);

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int value) { workerThreads = positive(value, 8); }
    public int getModelMaxConcurrency() { return modelMaxConcurrency; }
    public void setModelMaxConcurrency(int value) { modelMaxConcurrency = positive(value, 6); }
    public int getUserQueueCapacity() { return userQueueCapacity; }
    public void setUserQueueCapacity(int value) { userQueueCapacity = positive(value, 5); }
    public int getGlobalQueueCapacity() { return globalQueueCapacity; }
    public void setGlobalQueueCapacity(int value) { globalQueueCapacity = positive(value, 100); }
    public Duration getTaskTimeout() { return taskTimeout; }
    public void setTaskTimeout(Duration value) { taskTimeout = valid(value, Duration.ofSeconds(180)); }
    public Duration getUserMailboxIdle() { return userMailboxIdle; }
    public void setUserMailboxIdle(Duration value) { userMailboxIdle = valid(value, Duration.ofMinutes(10)); }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static Duration valid(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
