package com.example.spring.wechat.care.model;

import java.time.Instant;
import java.time.LocalDate;

public record CareTaskInstance(
        long id,
        long planId,
        long planVersionId,
        long taskTemplateId,
        long patientUserId,
        String title,
        String instructions,
        CareTaskType taskType,
        LocalDate scheduledFor,
        Instant dueAt,
        CareTaskStatus status,
        Long completedByUserId,
        Instant completedAt,
        String resultNote,
        int snoozeCount,
        Instant reminderEnqueuedAt,
        Instant followUpEnqueuedAt,
        Instant overdueNotifiedAt,
        String completionMode,
        Instant reportedAt,
        Instant lateCheckinDeadlineAt,
        Instant missedConfirmedAt,
        String missedReason,
        String idempotencyKey,
        long version,
        int gracePeriodMinutes,
        int escalationAfterMinutes,
        Instant createdAt,
        Instant updatedAt) {

    public CareTaskInstance(
            long id,
            long planId,
            long planVersionId,
            long taskTemplateId,
            long patientUserId,
            String title,
            String instructions,
            CareTaskType taskType,
            LocalDate scheduledFor,
            Instant dueAt,
            CareTaskStatus status,
            Long completedByUserId,
            Instant completedAt,
            String resultNote,
            int snoozeCount,
             Instant reminderEnqueuedAt,
             Instant overdueNotifiedAt,
             String idempotencyKey,
            long version,
            int gracePeriodMinutes,
            int escalationAfterMinutes,
            Instant createdAt,
            Instant updatedAt) {
        this(id, planId, planVersionId, taskTemplateId, patientUserId, title, instructions, taskType,
                scheduledFor, dueAt, status, completedByUserId, completedAt, resultNote, snoozeCount,
                reminderEnqueuedAt, null, overdueNotifiedAt, null, null, null, null, null, idempotencyKey, version,
                gracePeriodMinutes, escalationAfterMinutes,
                createdAt, updatedAt);
    }

    /** Compatibility constructor used by task tests and older callers. */
    public CareTaskInstance(
            long id,
            long planId,
            long planVersionId,
            long taskTemplateId,
            long patientUserId,
            String title,
            String instructions,
            CareTaskType taskType,
            LocalDate scheduledFor,
            Instant dueAt,
            CareTaskStatus status,
            Long completedByUserId,
            Instant completedAt,
            String resultNote,
            int snoozeCount,
            Instant reminderEnqueuedAt,
            Instant followUpEnqueuedAt,
            Instant overdueNotifiedAt,
            String idempotencyKey,
            long version,
            int gracePeriodMinutes,
            int escalationAfterMinutes,
            Instant createdAt,
            Instant updatedAt) {
        this(id, planId, planVersionId, taskTemplateId, patientUserId, title, instructions, taskType,
                scheduledFor, dueAt, status, completedByUserId, completedAt, resultNote, snoozeCount,
                reminderEnqueuedAt, followUpEnqueuedAt, overdueNotifiedAt, null, null, null, null, null,
                idempotencyKey, version, gracePeriodMinutes, escalationAfterMinutes, createdAt, updatedAt);
    }
}
