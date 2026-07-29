package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "care.notification.enabled", havingValue = "true", matchIfMissing = true)
public class CareNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CareNotificationScheduler.class);

    private final CareNotificationRepository repository;
    private final ReminderNotificationSender sender;
    private final CareProperties properties;
    private final Clock clock;

    public CareNotificationScheduler(
            CareNotificationRepository repository,
            ReminderNotificationSender sender,
            CareProperties properties,
            Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${care.notification.poll-interval-ms:15000}")
    public void poll() {
        processDue(clock.instant());
    }

    public void processDue(Instant now) {
        repository.releaseExpiredLocks(
                now.minusSeconds(properties.notification().lockTimeoutSeconds()), now);
        for (Long id : repository.findDueIds(now, properties.notification().batchSize())) {
            if (id == null || !repository.claim(id, now)) continue;
            repository.findById(id).ifPresent(notification -> deliver(notification, now));
        }
    }

    private void deliver(MedicalNotification notification, Instant now) {
        try {
            sender.sendText(notification.connectionId(), notification.recipientId(), notification.content());
            repository.markSent(notification.id(), now);
            log.info("照护通知发送成功，notificationId={}", notification.id());
        } catch (Exception exception) {
            boolean terminal = notification.retryCount() + 1 >= notification.maxRetryCount();
            repository.markFailed(
                    notification.id(), terminal, rootMessage(exception),
                    terminal ? null : now.plusSeconds(properties.notification().retryDelaySeconds()), now);
            log.warn("照护通知发送失败，notificationId={}, terminal={}", notification.id(), terminal);
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
