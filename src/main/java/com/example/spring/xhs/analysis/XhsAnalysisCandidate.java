package com.example.spring.xhs.analysis;

import com.example.spring.xhs.model.XhsMetrics;

import java.time.Instant;

public record XhsAnalysisCandidate(
        long postId,
        long projectId,
        String projectKey,
        String title,
        String content,
        String sourceUrl,
        Instant publishedAt,
        Instant collectedAt,
        XhsMetrics metrics) {
}
