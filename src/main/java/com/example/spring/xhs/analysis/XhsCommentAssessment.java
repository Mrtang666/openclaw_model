package com.example.spring.xhs.analysis;

import java.util.List;

public record XhsCommentAssessment(
        long analysisId,
        boolean negative,
        int riskScore,
        double confidence,
        String summary,
        List<String> evidence) {

    public XhsCommentAssessment {
        riskScore = Math.max(0, Math.min(riskScore, 100));
        confidence = Math.max(0, Math.min(confidence, 1));
        summary = summary == null ? "" : summary.strip();
        evidence = evidence == null ? List.of() : evidence.stream()
                .filter(java.util.Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).distinct().limit(5).toList();
    }
}
