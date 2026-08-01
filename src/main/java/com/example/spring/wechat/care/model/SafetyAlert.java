package com.example.spring.wechat.care.model;

import java.time.Instant;

public record SafetyAlert(
        long id,
        long patientUserId,
        String alertType,
        SafetySeverity severity,
        SafetyAlertStatus status,
        String evidenceType,
        Long evidenceId,
        String evidenceText,
        String idempotencyKey,
        Instant detectedAt,
        Long acknowledgedByUserId,
        Instant acknowledgedAt,
        Long resolvedByUserId,
        Instant resolvedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
