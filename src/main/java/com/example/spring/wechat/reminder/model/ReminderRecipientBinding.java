package com.example.spring.wechat.reminder.model;

import java.time.Instant;

public record ReminderRecipientBinding(
        String botId,
        String recipientId,
        String connectionId,
        String sessionKey,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt) {
}
