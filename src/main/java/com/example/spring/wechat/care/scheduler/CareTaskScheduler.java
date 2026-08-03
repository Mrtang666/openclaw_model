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
import com.example.spring.wechat.care.service.CareTaskActionTokenService;
import com.example.spring.wechat.care.service.CareWebLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CareTaskActionTokenService actionTokenService;
    private final CareWebLinkService webLinkService;

    @Autowired
    public CareTaskScheduler(
            CarePlanRepository planRepository,
            CareTaskRepository taskRepository,
            MedicalIdentityRepository identityRepository,
            CareNotificationRepository notificationRepository,
            CareTaskProperties taskProperties,
            CareProperties careProperties,
            CareTaskActionTokenService actionTokenService,
            CareWebLinkService webLinkService,
            Clock clock) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.identityRepository = identityRepository;
        this.notificationRepository = notificationRepository;
        this.taskProperties = taskProperties;
        this.careProperties = careProperties;
        this.actionTokenService = actionTokenService;
        this.webLinkService = webLinkService;
        this.clock = clock;
    }

    public CareTaskScheduler(
            CarePlanRepository planRepository,
            CareTaskRepository taskRepository,
            MedicalIdentityRepository identityRepository,
            CareNotificationRepository notificationRepository,
            CareTaskProperties taskProperties,
            CareProperties careProperties,
            Clock clock) {
        this(planRepository, taskRepository, identityRepository, notificationRepository,
                taskProperties, careProperties, null, null, clock);
    }

    @Scheduled(fixedDelayString = "${care.task.poll-interval-ms:15000}")
    public void poll() {
        process(clock.instant());
    }

    public void process(Instant now) {
        materialize(now);
        enqueueDueReminders(now);
        markOverdue(now);
        enqueueBackfillNotifications(now);
        markMissed(now);
        enqueueMissedNotifications(now);
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
            List<NotificationTarget> targets = patientTargets(task.patientUserId());
            if (targets.isEmpty()) {
                log.warn("照护任务暂未入队：患者没有可用微信通道，taskId={}, patientUserId={}",
                        task.id(), task.patientUserId());
                continue;
            }
            for (NotificationTarget target : targets) {
                enqueue(task, target, MedicalRole.PATIENT, "CARE_TASK_DUE",
                        dueReminderContent(task, target, MedicalRole.PATIENT, now), now);
            }
            taskRepository.markReminderEnqueued(task.id(), now);
        }
    }

    void markOverdue(Instant now) {
        for (Long taskId : taskRepository.findReadyToMarkOverdue(now, taskProperties.batchSize())) {
            if (taskId != null) taskRepository.markOverdue(taskId, now);
        }
    }

    void enqueueBackfillNotifications(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForFollowUp(now, taskProperties.batchSize())) {
            boolean queued = false;
            for (NotificationTarget target : patientTargets(task.patientUserId())) {
                enqueue(task, target, MedicalRole.PATIENT, "CARE_TASK_FOLLOW_UP",
                        backfillContent(task, target, MedicalRole.PATIENT, now), now);
                queued = true;
            }
            for (FamilyRecipient recipient : familyRecipients(task.patientUserId(), now, CarePermissions.PATIENT_TASK_BACKFILL)) {
                enqueue(task, recipient.target(), recipient.role(), "CARE_TASK_FOLLOW_UP",
                        backfillContent(task, recipient.target(), recipient.role(), now), now);
                queued = true;
            }
            if (queued) taskRepository.markFollowUpEnqueued(task.id(), now);
        }
    }

    void markMissed(Instant now) {
        for (Long taskId : taskRepository.findReadyToMarkMissed(now, taskProperties.batchSize())) {
            if (taskId != null) taskRepository.markMissed(taskId, now);
        }
    }

    void enqueueMissedNotifications(Instant now) {
        for (CareTaskInstance task : taskRepository.findReadyForOverdueNotification(
                now, taskProperties.batchSize())) {
            List<NotificationTarget> targets = familyTargets(task.patientUserId(), now);
            if (targets.isEmpty()) {
                log.warn("照护任务异常通知暂未入队：患者没有已授权家属微信通道，taskId={}, patientUserId={}",
                        task.id(), task.patientUserId());
                continue;
            }
            for (NotificationTarget target : targets) {
                enqueue(task, target, null, "CARE_TASK_MISSED",
                        missedNotificationContent(task), now);
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
            MedicalRole role,
            String type,
            String content,
            Instant now) {
        String idempotencyKey = "task:" + task.id() + ":" + type + ":" + targetHash(target);
        notificationRepository.enqueue(new MedicalNotification(
                0L, target.userId(), task.patientUserId(), target.connectionId(), target.recipientId(),
                type, "WECHAT", content, "PENDING", now, null, 0,
                careProperties.notification().maxRetryCount(), "", null, idempotencyKey, now, now));
    }

    private String dueReminderContent(
            CareTaskInstance task, NotificationTarget target, MedicalRole role, Instant now) {
        String actionUrl = actionUrl(task, target, role, now);
        String fallback = "完成 #" + task.id() + " 或 未完成 #" + task.id();
        String content = "【任务打卡】\n任务：" + task.title().strip()
                + (actionUrl.isBlank() ? "\n请回复：" + fallback
                : "\n请完成后点击链接确认；如无法打开链接，请回复：" + fallback);
        return withActionUrl(content, actionUrl);
    }

    private String backfillContent(
            CareTaskInstance task, NotificationTarget target, MedicalRole role, Instant now) {
        String actionUrl = actionUrl(task, target, role, now);
        String content = "【任务补卡】\n请确认“" + task.title().strip()
                + "”是否已经完成。\n已完成请回复：完成 #" + task.id()
                + "\n未完成请回复：未完成 #" + task.id()
                + "\n患者或家属均可在补卡截止前确认完成。";
        return withActionUrl(content, actionUrl);
    }

    private String missedNotificationContent(CareTaskInstance task) {
        return "【任务异常提醒】\n任务“" + task.title().strip()
                + "”补卡窗口已结束，系统已记录为未完成，请及时关注患者情况。";
    }

    private String actionUrl(CareTaskInstance task, NotificationTarget target, MedicalRole role, Instant now) {
        if (actionTokenService == null || webLinkService == null || !webLinkService.hasPublicBaseUrl()
                || task.lateCheckinDeadlineAt() == null
                || !task.lateCheckinDeadlineAt().isAfter(now)) {
            return "";
        }
        String rawToken = actionTokenService.issue(
                task.id(), target.userId(), role, task.lateCheckinDeadlineAt(), now);
        return webLinkService.taskActionUrl(rawToken);
    }

    private String withActionUrl(String content, String actionUrl) {
        return actionUrl == null || actionUrl.isBlank() ? content
                : content + "\n\n打开任务页面：" + actionUrl;
    }

    private List<NotificationTarget> patientTargets(long patientUserId) {
        Map<String, NotificationTarget> distinct = new LinkedHashMap<>();
        for (NotificationTarget target : identityRepository.listUserNotificationTargetsByRole(
                patientUserId, MedicalRole.PATIENT)) {
            distinct.putIfAbsent(target.userId() + ":" + target.recipientId(), target);
        }
        return List.copyOf(distinct.values());
    }

    private List<NotificationTarget> familyTargets(long patientUserId, Instant now) {
        return familyRecipients(patientUserId, now, CarePermissions.TASK_READ).stream()
                .map(FamilyRecipient::target).toList();
    }

    private List<FamilyRecipient> familyRecipients(long patientUserId, Instant now, String permission) {
        Map<String, FamilyRecipient> distinct = new LinkedHashMap<>();
        for (MedicalRole role : List.of(MedicalRole.FAMILY, MedicalRole.CAREGIVER)) {
            for (NotificationTarget target : identityRepository.listNotificationTargetsByRole(
                    patientUserId, role, permission, now)) {
                distinct.put(target.userId() + ":" + target.connectionId() + ":" + target.recipientId(),
                        new FamilyRecipient(target, role));
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

    private record FamilyRecipient(NotificationTarget target, MedicalRole role) {
    }
}
