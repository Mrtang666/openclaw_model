package com.example.spring.xhs.report;

import java.time.Instant;

public record XhsReportPostSummary(
        long postId,
        String title,
        String summary,
        String sentiment,
        String riskCategory,
        int riskScore,
        String riskSource,
        int bodyRiskScore,
        int negativeCommentCount,
        int highestCommentRiskScore,
        int negativeImageCount,
        int highestImageRiskScore,
        Instant publishedAt) {

    public XhsReportPostSummary(long postId, String title, String summary, String sentiment,
                                String riskCategory, int riskScore, Instant publishedAt) {
        this(postId, title, summary, sentiment, riskCategory, riskScore, "正文",
                riskScore, 0, 0, 0, 0, publishedAt);
    }
}
