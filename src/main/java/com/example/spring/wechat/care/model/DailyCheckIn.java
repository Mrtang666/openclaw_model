package com.example.spring.wechat.care.model;

import java.time.Instant;
import java.time.LocalDate;

public record DailyCheckIn(
        long id,
        long patientUserId,
        long submittedByUserId,
        LocalDate checkinDate,
        String sleepStatus,
        String mealStatus,
        String hydrationStatus,
        String moodStatus,
        String activityStatus,
        Boolean medicationConfirmed,
        String incidentType,
        String originalText,
        String sourceType,
        String status,
        String idempotencyKey,
        long version,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {
}
