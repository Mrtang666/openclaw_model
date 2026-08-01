package com.example.spring.xhs.analysis;

import java.time.Instant;

public record XhsAnalysisResult(
        long postId,
        String analysisVersion,
        XhsSemanticAssessment semantic,
        XhsRiskAssessment risk,
        String reviewStatus,
        Instant analyzedAt) {
}
