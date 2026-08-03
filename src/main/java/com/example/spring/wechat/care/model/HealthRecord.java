package com.example.spring.wechat.care.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One observation may contain one measurement or one free-text health report. */
public record HealthRecord(
        long id,
        long patientUserId,
        long recordedByUserId,
        MedicalRole recorderRole,
        HealthRecordCategory category,
        BigDecimal primaryValue,
        BigDecimal secondaryValue,
        String unit,
        String recordText,
        String sourceType,
        Instant occurredAt,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {
}
