package com.example.spring.wechat.care.model;

import java.time.Instant;

public record CarePlan(
        long id,
        long patientUserId,
        CarePlanType planType,
        String title,
        CarePlanStatus status,
        boolean clinicalReviewRequired,
        int currentRevision,
        long createdByUserId,
        Instant submittedAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        Instant activatedAt,
        Instant endedAt,
        String idempotencyKey,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
