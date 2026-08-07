package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "xhs.comment-analysis")
public record XhsCommentAnalysisProperties(
        boolean enabled,
        String version,
        int batchSize,
        int maxAttempts,
        int minimumRuleRiskScore,
        int minimumLikes,
        Duration claimTimeout) {

    public XhsCommentAnalysisProperties {
        version = version == null || version.isBlank() ? "xhs-comment-v1" : version.strip();
        batchSize = Math.max(1, Math.min(batchSize <= 0 ? 10 : batchSize, 20));
        maxAttempts = Math.max(1, Math.min(maxAttempts <= 0 ? 3 : maxAttempts, 10));
        minimumRuleRiskScore = Math.max(0, Math.min(
                minimumRuleRiskScore <= 0 ? 25 : minimumRuleRiskScore, 100));
        minimumLikes = Math.max(0, minimumLikes);
        claimTimeout = claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                ? Duration.ofMinutes(15) : claimTimeout;
    }
}
