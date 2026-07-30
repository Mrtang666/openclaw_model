package com.example.spring.xhs.schedule;

import java.time.Instant;
import java.util.List;

public record XhsReportScheduleView(
        long id,
        String projectKey,
        String projectName,
        String name,
        String frequency,
        String runTime,
        Integer dayOfWeek,
        Integer dayOfMonth,
        String timezone,
        List<String> formats,
        boolean collectBeforeReport,
        int collectionLimit,
        int topPostLimit,
        List<String> emailRecipients,
        String wechatConnectionId,
        String wechatRecipientId,
        boolean enabled,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant createdAt,
        Instant updatedAt) {
}
