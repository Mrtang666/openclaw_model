package com.example.spring.xhs.analysis;

import java.util.List;

public record XhsImageAssessment(
        boolean negative,
        int riskScore,
        boolean containsProduct,
        String summary,
        List<String> evidence) {

    public XhsImageAssessment {
        riskScore = Math.max(0, Math.min(riskScore, 100));
        summary = summary == null ? "" : summary.strip();
        evidence = evidence == null ? List.of() : evidence.stream()
                .filter(java.util.Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).distinct().limit(8).toList();
    }
}
