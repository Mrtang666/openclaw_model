package com.example.spring.wechat.reminder.repository;

import com.example.spring.wechat.reminder.model.ReminderRecipientBinding;

import java.time.Instant;
import java.util.Optional;

public interface ReminderRecipientBindingRepository {

    Optional<ReminderRecipientBinding> find(String botId, String recipientId);

    void upsert(
            String botId,
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant seenAt);
}
