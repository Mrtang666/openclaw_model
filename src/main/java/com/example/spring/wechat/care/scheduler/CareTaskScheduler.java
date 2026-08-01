package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CarePlanRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.care.service.CarePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "care.task.enabled", havingValue = "true")
public class CareTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(CareTaskScheduler.class);

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
        log.info("照护任务调度已启用，pollIntervalMs={}, batchSize={}, generationHorizonDays={}",
                taskProperties.pollIntervalMs(), taskProperties.batchSize(), taskProperties.generationHorizonDays());
    }

    @Scheduled(fixedDelayString = "${care.task.poll-interval-ms:15000}")
    public void poll() {
        process(clock.instant());
    }

    public void process(Instant now) {
        materialize(now);
        enqueueDueReminders(now);
        enqueueFollowUps(now);
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
            List<NotificationTarget> targets = identityRepository.listUserNotificationTargetsByRole(
                    task.patientUserId(), MedicalRole.PATIENT);
            if (targets.isEmpty()) {
                log.warn("照护任务暂未入队：患者没有可用微信通道，taskId={}, patientUserId={}",
                        task.id(), task.patientUserId());
                continue;
            }
            for (NotificationTarget target : targets) {
            for (NotificationTarget target : patientTargets(task.patientUserId())) {
                enqueue(task, target, "CARE_TASK_DUE",
                        dueReminderContent(task), now);
            }
            taskRepository.markReminderEnqueued(task.id(), now);
            log.info("照护任务提醒已入队，taskId={}, patientUserId={}, targetCount={}, dueAt={}",
                    task.id(), task.patientUserId(), targets.size(), task.dueAt());
        }
    }

    void markOverdue(Instant now) {
        for (Long taskId : taskRepository.findReadyToMarkOverdue(now, taskProperties.batchSize())) {
            if (taskId != null) taskRepository.markOverdue(taskId, now);
        }
    }

    void enqueueFollowUps(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForFollowUp(now, taskProperties.batchSize())) {
            for (NotificationTarget target : patientTargets(task.patientUserId())) {
                enqueue(task, target, "CARE_TASK_FOLLOW_UP", followUpContent(task), now);
            }
            taskRepository.markFollowUpEnqueued(task.id(), now);
        }
    }

    void enqueueOverdueNotifications(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForOverdueNotification(
                now, taskProperties.batchSize())) {
            List<NotificationTarget> targets = familyTargets(task.patientUserId(), now);
            if (targets.isEmpty()) {
                log.warn("照护任务异常通知暂未入队：患者没有已授权家属微信通道，taskId={}, patientUserId={}",
                        task.id(), task.patientUserId());
                continue;
            }
            for (NotificationTarget target : targets) {
                enqueue(task, target, "CARE_TASK_OVERDUE",
                        overdueNotificationContent(task), now);
            }
            taskRepository.markOverdueNotified(task.id(), now);
            log.info("照护任务异常通知已入队，taskId={}, patientUserId={}, targetCount={}",
                    task.id(), task.patientUserId(), targets.size());
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

    private String dueReminderContent(CareTaskInstance task) {
        return """
                【任务打卡】
                %s

                请回复：
                完成 #%d
                未完成 #%d
                """.formatted(task.title().strip(), task.id(), task.id()).strip();
    }

    private String overdueNotificationContent(CareTaskInstance task) {
        return "【任务异常提醒】\n患者有一项照护任务未在计划时间内确认，请及时关注。\n任务："
                + task.title() + "（#" + task.id() + "）";
    }

    private List<NotificationTarget> familyTargets(long patientUserId, Instant now) {
        Map<String, NotificationTarget> distinct = new LinkedHashMap<>();
        for (MedicalRole role : List.of(MedicalRole.FAMILY, MedicalRole.CAREGIVER)) {
            for (NotificationTarget target : identityRepository.listNotificationTargetsByRole(
                    patientUserId, role, CarePermissions.TASK_READ, now)) {
                distinct.put(target.userId() + ":" + target.connectionId() + ":" + target.recipientId(), target);
            }
        }
        return List.copyOf(distinct.values());
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
