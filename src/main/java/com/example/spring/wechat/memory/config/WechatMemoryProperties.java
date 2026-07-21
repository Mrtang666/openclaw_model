package com.example.spring.wechat.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信端上下文记忆的保留策略与会话边界配置。
 */
@ConfigurationProperties(prefix = "wechat.memory")
public record WechatMemoryProperties(
        int sessionIdleMinutes,
        int rawRetentionDays,
        int recentTurnLimit,
        int rollingSummaryTurnThreshold) {

    public WechatMemoryProperties {
        sessionIdleMinutes = sessionIdleMinutes <= 0 ? 60 : sessionIdleMinutes;
        rawRetentionDays = rawRetentionDays <= 0 ? 30 : rawRetentionDays;
        recentTurnLimit = recentTurnLimit <= 0 ? 10 : recentTurnLimit;
        rollingSummaryTurnThreshold =
                rollingSummaryTurnThreshold <= 0 ? 20 : rollingSummaryTurnThreshold;
    }
}
