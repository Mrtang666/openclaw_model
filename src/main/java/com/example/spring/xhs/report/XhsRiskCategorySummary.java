package com.example.spring.xhs.report;

public record XhsRiskCategorySummary(
        String riskCategory,
        int postCount,
        int averageRiskScore,
        int maximumRiskScore) {
}
