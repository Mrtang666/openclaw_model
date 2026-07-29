package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CarePlanRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.care.service.CarePermissions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(name = "care.task.enabled", havingValue = "true")
public class CareTaskScheduler {

    private final CarePlanRepository planRepository;
    private final CareTaskRepository taskRepository;
    private final MedicalIdentityRepository identityRepository;
    private final CareNotificationRepository notificationRepository;
    private final CareTaskProperties taskProperties;
    private final CareProperties careProperties;
    private final Clock clock;

    public CareTaskScheduler(
            CarePlanRepository planRepository,
            CareTaskRepository taskRepository,
            MedicalIdentityRepository identityRepository,
            CareNotificationRepository notificationRepository,
            CareTaskProperties taskProperties,
            CareProperties careProperties,
            Clock clock) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.identityRepository = identityRepository;
        this.notificationRepository = notificationRepository;
        this.taskProperties = taskProperties;
        this.careProperties = careProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${care.task.poll-interval-ms:60000}")
    public void poll() {
        process(clock.instant());
    }

    public void process(Instant now) {
        materialize(now);
        enqueueDueReminders(now);
        markOverdue(now);
        enqueueOverdueNotifications(now);
    }

    void materialize(Instant now) {
        for (CareTaskTemplate template : planRepository.listActiveTemplates()) {
            ZoneId zone = ZoneId.of(template.timezone());
            LocalDate localToday = ZonedDateTime.ofInstant(now, zone).toLocalDate();
            for (int day = 0; day <= taskProperties.generationHorizonDays(); day++) {
                LocalDate date = localToday.plusDays(day);
                if (!isScheduledFor(template, date)) continue;
                Instant dueAt = date.atTime(template.localTime()).atZone(zone).toInstant();
                taskRepository.createInstanceIfAbsent(template, date, dueAt, now);
            }
        }
    }

    void enqueueDueReminders(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForReminder(now, taskProperties.batchSize())) {
            List<NotificationTarget> targets = identityRepository.listUserNotificationTargets(task.patientUserId());
            for (NotificationTarget target : targets) {
                enqueue(task, target, "CARE_TASK_DUE",
                        "您有一项照护任务需要处理，请打开患者端查看。任务编号 #" + task.id(), now);
            }
            taskRepository.markReminderEnqueued(task.id(), now);
        }
    }

    void markOverdue(Instant now) {
        for (Long taskId : taskRepository.findReadyToMarkOverdue(now, taskProperties.batchSize())) {
            if (taskId != null) taskRepository.markOverdue(taskId, now);
        }
    }

    void enqueueOverdueNotifications(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForOverdueNotification(
                now, taskProperties.batchSize())) {
            List<NotificationTarget> targets = identityRepository.listNotificationTargets(
                    task.patientUserId(), CarePermissions.TASK_READ, now);
            for (NotificationTarget target : targets) {
                enqueue(task, target, "CARE_TASK_OVERDUE",
                        "患者有一项照护任务长时间未确认，请登录照护端查看。任务编号 #" + task.id(), now);
            }
            taskRepository.markOverdueNotified(task.id(), now);
        }
    }

    static boolean isScheduledFor(CareTaskTemplate template, LocalDate date) {
        if (date.isBefore(template.startDate())
                || (template.endDate() != null && date.isAfter(template.endDate()))) {
            return false;
        }
        if (template.scheduleType() == CareTaskScheduleType.ONCE) {
            return date.equals(template.scheduledDate());
        }
        if (template.scheduleType() == CareTaskScheduleType.WEEKLY) {
            return DayOfWeek.of(template.dayOfWeek()) == date.getDayOfWeek();
        }
        return template.scheduleType() == CareTaskScheduleType.DAILY;
    }

    private void enqueue(
            CareTaskInstance task,
            NotificationTarget target,
            String type,
            String content,
            Instant now) {
        String idempotencyKey = "task:" + task.id() + ":" + type + ":" + targetHash(target);
        notificationRepository.enqueue(new MedicalNotification(
                0L, target.userId(), task.patientUserId(), target.connectionId(), target.recipientId(),
                type, "WECHAT", content, "PENDING", now, null, 0,
                careProperties.notification().maxRetryCount(), "", null, idempotencyKey, now, now));
    }

    private String targetHash(NotificationTarget target) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (target.connectionId() + ":" + target.recipientId()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成照护通知幂等键", exception);
        }
    }
}
