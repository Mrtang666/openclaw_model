package com.example.spring.wechat.care.model;

import java.time.Instant;

public record MedicalNotification(
        long id,
        long toUserId,
        Long patientUserId,
        String connectionId,
        String recipientId,
        String notificationType,
        String channel,
        String content,
        String status,
        Instant scheduledAt,
        Instant sentAt,
        int retryCount,
        int maxRetryCount,
        String lastError,
        Instant lockedAt,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {
}
