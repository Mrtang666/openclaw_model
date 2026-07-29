package com.example.spring.wechat.care.model;

import java.time.Instant;
import java.util.Set;

public record PatientRelation(
        long id,
        long viewerUserId,
        long patientUserId,
        MedicalRole relationRole,
        String relationLabel,
        String status,
        long version,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt) {
}
