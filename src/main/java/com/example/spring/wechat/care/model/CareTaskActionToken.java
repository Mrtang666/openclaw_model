package com.example.spring.wechat.care.model;

import java.time.Instant;

public record CareTaskActionToken(
        long id,
        long taskInstanceId,
        long actorUserId,
        MedicalRole actorRole,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt,
        Instant updatedAt) {
}
