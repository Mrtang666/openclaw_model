package com.example.spring.task;

import java.time.Instant;

public record ImageTaskSession(
    String userId,
    TaskStatus status,
    ImageTaskBrief brief,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt) {
}
