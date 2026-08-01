package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.reminder.model.ReminderRecipientBinding;
import com.example.spring.wechat.reminder.repository.ReminderRecipientBindingRepository;
import com.example.spring.wechat.reminder.repository.ReminderTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class ReminderRecipientBindingService {

    private final ReminderRecipientBindingRepository bindingRepository;
    private final ReminderTaskRepository taskRepository;
    private final Clock clock;

    public ReminderRecipientBindingService(
            ReminderRecipientBindingRepository bindingRepository,
            ReminderTaskRepository taskRepository,
            Clock clock) {
        this.bindingRepository = bindingRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public int bind(
            String botId,
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant seenAt) {
        String cleanBotId = required(botId, "botId");
        String cleanRecipientId = required(recipientId, "recipientId");
        String cleanConnectionId = required(connectionId, "connectionId");
        String cleanSessionKey = required(sessionKey, "sessionKey");
        Instant now = seenAt == null ? clock.instant() : seenAt;
        Optional<ReminderRecipientBinding> previous =
                bindingRepository.find(cleanBotId, cleanRecipientId);
        bindingRepository.upsert(
                cleanBotId, cleanRecipientId, cleanConnectionId, cleanSessionKey, now);
        if (previous.isEmpty()) {
            return taskRepository.adoptSingleKnownConnection(
                    cleanRecipientId, cleanConnectionId, cleanSessionKey, now);
        }
        if (previous.get().connectionId().equals(cleanConnectionId)) {
            return 0;
        }
        return taskRepository.rebindConnection(
                previous.get().connectionId(),
                cleanRecipientId,
                cleanConnectionId,
                cleanSessionKey,
                now);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.strip();
    }
}
