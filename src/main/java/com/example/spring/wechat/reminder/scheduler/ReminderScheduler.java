package com.example.spring.wechat.reminder.scheduler;

import com.example.spring.wechat.reminder.config.ReminderProperties;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.repository.ReminderTaskRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderScheduleCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "reminder.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderTaskRepository repository;
    private final ReminderNotificationSender notificationSender;
    private final ReminderProperties properties;
    private final Clock clock;
    private final ReminderScheduleCalculator scheduleCalculator = new ReminderScheduleCalculator();

    public ReminderScheduler(
            ReminderTaskRepository repository,
            ReminderNotificationSender notificationSender,
            ReminderProperties properties,
            Clock clock) {
        this.repository = repository;
        this.notificationSender = notificationSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${reminder.scheduler.poll-interval-ms:15000}")
    public void poll() {
        processDue(clock.instant());
    }

    public void processDue(Instant now) {
        Instant current = now == null ? clock.instant() : now;
        repository.releaseExpiredLocks(
                current.minusSeconds(properties.scheduler().lockTimeoutSeconds()), current);
        for (Long id : repository.findDueIds(current, properties.scheduler().batchSize())) {
            if (id == null || !repository.claimForDelivery(id, current)) {
                continue;
            }
            repository.findById(id).ifPresent(task -> deliver(task, current));
        }
    }

    private void deliver(ReminderTask task, Instant now) {
        Instant scheduledAt = task.nextExecuteAt();
        if (scheduledAt == null) {
            return;
        }
        String idempotencyKey = task.id() + ":" + scheduledAt.toEpochMilli();
        try {
            repository.recordDeliveryStarted(task.id(), scheduledAt, idempotencyKey, now);
            notificationSender.sendText(
                    task.connectionId(), task.recipientId(), ReminderReplyFormatter.notification(task));
            Instant nextExecution = scheduleCalculator.nextExecution(
                    scheduledAt, task.repeatType(), ZoneId.of(task.timezone()), now);
            repository.markDelivered(task.id(), idempotencyKey, nextExecution, now);
            log.info("提醒发送成功，taskId={}, recipientId={}", task.id(), task.recipientId());
        } catch (Exception exception) {
            boolean terminal = task.retryCount() + 1 >= task.maxRetryCount();
            Instant retryAt = terminal
                    ? null
                    : now.plusSeconds(properties.delivery().retryDelaySeconds());
            String message = rootMessage(exception);
            repository.markDeliveryFailed(task.id(), idempotencyKey, retryAt, terminal, message, now);
            log.warn("提醒发送失败，taskId={}, terminal={}, error={}", task.id(), terminal, message);
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
