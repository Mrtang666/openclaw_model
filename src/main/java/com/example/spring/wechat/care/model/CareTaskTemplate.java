package com.example.spring.wechat.care.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CareTaskTemplate(
        long id,
        long planVersionId,
        long planId,
        long patientUserId,
        CareTaskType taskType,
        String title,
        String instructions,
        CareTaskScheduleType scheduleType,
        LocalTime localTime,
        LocalDate scheduledDate,
        Integer dayOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        int followUpAfterMinutes,
        int gracePeriodMinutes,
        int escalationAfterMinutes,
        boolean enabled,
        int sortOrder,
        String timezone,
        Instant createdAt) {

    public CareTaskTemplate(
            long id,
            long planVersionId,
            long planId,
            long patientUserId,
            CareTaskType taskType,
            String title,
            String instructions,
            CareTaskScheduleType scheduleType,
            LocalTime localTime,
            LocalDate scheduledDate,
            Integer dayOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            int gracePeriodMinutes,
            int escalationAfterMinutes,
            boolean enabled,
            int sortOrder,
            String timezone,
            Instant createdAt) {
        this(id, planVersionId, planId, patientUserId, taskType, title, instructions, scheduleType,
                localTime, scheduledDate, dayOfWeek, startDate, endDate,
                Math.max(1, gracePeriodMinutes / 2), gracePeriodMinutes, escalationAfterMinutes,
                enabled, sortOrder, timezone, createdAt);
    }
}
