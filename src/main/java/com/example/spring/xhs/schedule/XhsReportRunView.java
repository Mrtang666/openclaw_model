package com.example.spring.xhs.schedule;

import java.time.Instant;
import java.util.List;

public record XhsReportRunView(
        long id,
        long scheduleId,
        String scheduleName,
        String projectKey,
        String projectName,
        Instant scheduledFor,
        Instant periodStart,
        Instant periodEnd,
        String status,
        String partialReason,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        List<Artifact> artifacts,
        List<Delivery> deliveries,
        int pendingDeliveries,
        int failedDeliveries) {

    public record Artifact(long id, String format, String fileName, long sizeBytes, Instant createdAt) {
    }

    public record Delivery(long id, String channel, String target, String status, int attemptCount,
                           String lastError, Instant nextAttemptAt, Instant sentAt) {
    }
}
