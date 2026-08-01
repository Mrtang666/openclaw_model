package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.model.MedicalRole;
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
        log.info("照护通知调度已启用，pollIntervalMs={}, batchSize={}, maxRetryCount={}",
                properties.notification().pollIntervalMs(), properties.notification().batchSize(),
                properties.notification().maxRetryCount());
    }

    @Scheduled(fixedDelayString = "${care.notification.poll-interval-ms:15000}")
    public void poll() {
        processDue(clock.instant());
    }

    public void processDue(Instant now) {
        repository.requeueConnectionUnavailablePatientNotifications(now);
        repository.releaseExpiredLocks(
                now.minusSeconds(properties.notification().lockTimeoutSeconds()), now);
        for (Long id : repository.findDueIds(now, properties.notification().batchSize())) {
            if (id == null || !repository.claim(id, now)) continue;
            repository.findById(id).ifPresent(notification -> deliver(notification, now));
        }
    }

    private void deliver(MedicalNotification notification, Instant now) {
        try {
            sendToFirstAvailableTarget(notification);
            repository.markSent(notification.id(), now);
            log.info("照护通知发送成功，notificationId={}", notification.id());
        } catch (Exception exception) {
            lastError = rootMessage(exception);
            if (isConnectionUnavailable(lastError)) {
                repository.deferUntilConnectionAvailable(
                        notification.id(), lastError, now.plusSeconds(connectionRetryDelaySeconds()), now);
                log.info("照护通知等待接收方重新登录，notificationId={}", notification.id());
                return;
            }
            boolean terminal = notification.retryCount() + 1 >= notification.maxRetryCount();
            String error = rootMessage(exception);
            repository.markFailed(
                    notification.id(), terminal, error,
                    terminal ? null : now.plusSeconds(properties.notification().retryDelaySeconds()), now);
            log.warn("照护通知发送失败，notificationId={}, terminal={}, error={}",
                    notification.id(), terminal, error);
        }
    }

    private boolean isConnectionUnavailable(String message) {
        String value = message == null ? "" : message;
        return value.contains("微信连接当前不可用")
                || value.contains("没有可用微信连接");
    }

    private long connectionRetryDelaySeconds() {
        return Math.max(60L, properties.notification().retryDelaySeconds());
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
        List<NotificationTarget> latestTargets = latestTargets(notification);
        if (latestTargets != null) {
            for (NotificationTarget target : latestTargets) {
                addTarget(targets, target);
            }
        }
        return List.copyOf(targets.values());
    }

    private List<NotificationTarget> latestTargets(MedicalNotification notification) {
        if ("CARE_PLAN_TO_PATIENT".equals(notification.notificationType())
                || "CARE_TASK_DUE".equals(notification.notificationType())
                || "CARE_TASK_FOLLOW_UP".equals(notification.notificationType())) {
            return identityRepository.listUserNotificationTargetsByRole(notification.toUserId(), MedicalRole.PATIENT);
        }
        if ("CARE_PLAN_TO_FAMILY".equals(notification.notificationType())) {
            List<NotificationTarget> caregiverTargets = identityRepository.listUserNotificationTargetsByRole(
                    notification.toUserId(), MedicalRole.CAREGIVER);
            if (!caregiverTargets.isEmpty()) {
                return caregiverTargets;
            }
            return identityRepository.listUserNotificationTargetsByRole(notification.toUserId(), MedicalRole.FAMILY);
        }
        return identityRepository.listUserNotificationTargets(notification.toUserId());
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
