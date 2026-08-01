package com.example.spring.wechat.reminder.model;

import java.time.Instant;

public record ReminderTask(
        long id,
        Long parentTaskId,
        String sessionKey,
        String connectionId,
        String recipientId,
        String title,
        String content,
        ReminderRepeatType repeatType,
        String timezone,
        Instant nextExecuteAt,
        ReminderStatus status,
        int retryCount,
        int maxRetryCount,
        Instant lockedAt,
        String lastError,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ReminderTask {
        sessionKey = clean(sessionKey);
        connectionId = clean(connectionId);
        recipientId = clean(recipientId);
        title = clean(title);
        content = clean(content);
        repeatType = repeatType == null ? ReminderRepeatType.ONCE : repeatType;
        timezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.strip();
        status = status == null ? ReminderStatus.ACTIVE : status;
        retryCount = Math.max(0, retryCount);
        maxRetryCount = maxRetryCount <= 0 ? 3 : maxRetryCount;
        lastError = clean(lastError);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
