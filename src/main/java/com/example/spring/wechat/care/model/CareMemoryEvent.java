package com.example.spring.wechat.care.model;

import java.time.Instant;

public record CareMemoryEvent(
        long id,
        long patientUserId,
        long recordedByUserId,
        String originalText,
        String normalizedText,
        Instant occurredAt,
        String peopleJson,
        String placeText,
        String sourceType,
        String sourceMessageId,
        MemoryVisibility visibility,
        MemoryEventStatus status,
        Long confirmedByUserId,
        Instant confirmedAt,
        long version,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {
}
