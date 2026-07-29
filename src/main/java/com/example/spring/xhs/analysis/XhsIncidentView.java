package com.example.spring.xhs.analysis;

import java.time.Instant;

public record XhsIncidentView(
        long incidentId,
        String projectKey,
        String title,
        String riskCategory,
        String status,
        int riskScore,
        String riskLevel,
        int postCount,
        Instant firstSeenAt,
        Instant lastSeenAt) {
}
