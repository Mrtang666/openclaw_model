package com.example.spring.xhs.report;

import java.time.Instant;

public record XhsReportPostSummary(
        long postId,
        String title,
        String summary,
        String sentiment,
        String riskCategory,
        int riskScore,
        Instant publishedAt) {
}
