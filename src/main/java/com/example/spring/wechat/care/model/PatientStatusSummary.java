package com.example.spring.wechat.care.model;

import java.time.Instant;

public record PatientStatusSummary(
        long patientUserId,
        String patientUserCode,
        String patientDisplayName,
        int checkInCount,
        int openAlertCount,
        int urgentAlertCount,
        int pendingMemoryCount,
        Instant latestCheckInAt,
        Instant generatedAt) {
}
