package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

@Component
@ConditionalOnProperty(name = "care.notification.enabled", havingValue = "true")
public class CareNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CareNotificationScheduler.class);

    private final CareNotificationRepository repository;
    private final MedicalIdentityRepository identityRepository;
    private final ReminderNotificationSender sender;
    private final CareProperties properties;
    private final Clock clock;

    public CareNotificationScheduler(
            CareNotificationRepository repository,
            MedicalIdentityRepository identityRepository,
            ReminderNotificationSender sender,
            CareProperties properties,
            Clock clock) {
        this.repository = repository;
        this.identityRepository = identityRepository;
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
        String lastError = "";
        try {
            sendToFirstAvailableTarget(notification);
            repository.markSent(notification.id(), now);
            log.info("照护通知发送成功，notificationId={}", notification.id());
        } catch (Exception exception) {
            lastError = rootMessage(exception);
            boolean terminal = notification.retryCount() + 1 >= notification.maxRetryCount();
            repository.markFailed(
                    notification.id(), terminal, lastError,
                    terminal ? null : now.plusSeconds(properties.notification().retryDelaySeconds()), now);
            log.warn("照护通知发送失败，notificationId={}, terminal={}, error={}",
                    notification.id(), terminal, lastError);
        }
    }

    private void sendToFirstAvailableTarget(MedicalNotification notification) {
        List<NotificationTarget> targets = deliveryTargets(notification);
        RuntimeException lastFailure = null;
        for (NotificationTarget target : targets) {
            try {
                sender.sendText(target.connectionId(), target.recipientId(), notification.content());
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("通知接收用户当前没有可用微信连接");
    }

    private List<NotificationTarget> deliveryTargets(MedicalNotification notification) {
        LinkedHashMap<String, NotificationTarget> targets = new LinkedHashMap<>();
        addTarget(targets, new NotificationTarget(
                notification.toUserId(), notification.connectionId(), notification.recipientId()));
        List<NotificationTarget> latestTargets = identityRepository.listUserNotificationTargets(notification.toUserId());
        if (latestTargets != null) {
            for (NotificationTarget target : latestTargets) {
                addTarget(targets, target);
            }
        }
        return List.copyOf(targets.values());
    }

    private void addTarget(LinkedHashMap<String, NotificationTarget> targets, NotificationTarget target) {
        if (target == null) {
            return;
        }
        if (target.connectionId() == null || target.connectionId().isBlank()
                || target.recipientId() == null || target.recipientId().isBlank()) {
            return;
        }
        targets.putIfAbsent(target.connectionId().strip() + ":" + target.recipientId().strip(), target);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
