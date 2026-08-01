package com.example.spring.xhs.analysis;

import java.util.List;

public record XhsSemanticAssessment(
        XhsSentiment sentiment,
        double sentimentScore,
        List<String> aspects,
        String riskCategory,
        int severity,
        double confidence,
        String summary,
        List<String> evidence) {

    public XhsSemanticAssessment {
        sentiment = sentiment == null ? XhsSentiment.NEUTRAL : sentiment;
        sentimentScore = Math.max(-1, Math.min(1, sentimentScore));
        aspects = clean(aspects);
        riskCategory = riskCategory == null || riskCategory.isBlank() ? "GENERAL" : riskCategory.strip().toUpperCase(java.util.Locale.ROOT);
        severity = Math.max(1, Math.min(5, severity));
        confidence = Math.max(0, Math.min(1, confidence));
        summary = summary == null ? "" : summary.strip();
        evidence = clean(evidence);
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(java.util.Objects::nonNull).map(String::strip).filter(value -> !value.isBlank()).distinct().limit(8).toList();
    }
}
