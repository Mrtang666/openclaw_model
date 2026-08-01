package com.example.spring.xhs.analysis;

import java.time.Instant;

public record XhsOpinionView(
        String projectKey,
        String title,
        String summary,
        XhsSentiment sentiment,
        String riskCategory,
        int riskScore,
        String riskLevel,
        String sourceUrl,
        Instant publishedAt,
        Instant analyzedAt) {
}
