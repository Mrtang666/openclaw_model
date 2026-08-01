package com.example.spring.wechat.care.model;

import java.time.Instant;

public record MedicalUser(
        long id,
        String userCode,
        String displayName,
        String status,
        long version,
        Instant firstSeenAt,
        Instant lastActiveAt,
        Instant createdAt,
        Instant updatedAt) {
}
