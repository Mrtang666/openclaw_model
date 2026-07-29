package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xhs.analysis")
public record XhsAnalysisProperties(
        boolean enabled,
        String version,
        int batchSize,
        int minimumIncidentRiskScore,
        double reviewConfidenceThreshold) {

    public XhsAnalysisProperties {
        version = version == null || version.isBlank() ? "xhs-opinion-v1" : version.strip();
        batchSize = Math.max(1, Math.min(batchSize <= 0 ? 20 : batchSize, 100));
        minimumIncidentRiskScore = Math.max(0, Math.min(
                minimumIncidentRiskScore <= 0 ? 60 : minimumIncidentRiskScore, 100));
        reviewConfidenceThreshold = reviewConfidenceThreshold <= 0 || reviewConfidenceThreshold > 1
                ? 0.65
                : reviewConfidenceThreshold;
    }
}
