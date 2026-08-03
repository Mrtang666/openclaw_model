package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.HealthRecord;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.model.SafetyAlert;
import com.example.spring.wechat.care.model.SafetyAlertStatus;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.care.repository.SafetyAlertRepository;
import com.example.spring.wechat.care.rules.SafetyRuleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SafetyAlertService {

    private final SafetyAlertRepository repository;
    private final MedicalIdentityRepository identityRepository;
    private final CareNotificationRepository notificationRepository;
    private final CareAuthorizationService authorizationService;
    private final CareProperties properties;
    private final Clock clock;

    public SafetyAlertService(
            SafetyAlertRepository repository,
            MedicalIdentityRepository identityRepository,
            CareNotificationRepository notificationRepository,
            CareAuthorizationService authorizationService,
            CareProperties properties,
            Clock clock) {
        this.repository = repository;
        this.identityRepository = identityRepository;
        this.notificationRepository = notificationRepository;
        this.authorizationService = authorizationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SafetyAlert createFromCheckIn(
            long patientUserId,
            long checkInId,
            SafetyRuleEngine.AlertCandidate candidate) {
        String idempotencyKey = "checkin:" + checkInId + ":" + candidate.alertType();
        return repository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            Instant now = clock.instant();
            SafetyAlert alert = repository.save(new SafetyAlert(
                    0L, patientUserId, candidate.alertType(), candidate.severity(), SafetyAlertStatus.OPEN,
                    "DAILY_CHECKIN", checkInId, candidate.evidenceText(), idempotencyKey, now,
                    null, null, null, null, 0L, now, now));
            if (candidate.severity() == com.example.spring.wechat.care.model.SafetySeverity.URGENT) {
                enqueueDoctorNotifications(alert, now);
            } else {
                enqueueNotifications(alert, now);
            }
            return alert;
        });
    }

    @Transactional
    public SafetyAlert createFromHealthRecord(
            HealthRecord record,
            SafetyRuleEngine.AlertCandidate candidate) {
        String idempotencyKey = "health:" + record.id() + ":" + candidate.alertType();
        return repository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            Instant now = clock.instant();
            String evidence = candidate.evidenceText() + "（记录值：" + recordValue(record) + "）";
            SafetyAlert alert = repository.save(new SafetyAlert(
                    0L, record.patientUserId(), candidate.alertType(), candidate.severity(), SafetyAlertStatus.OPEN,
                    "HEALTH_RECORD", record.id(), evidence, idempotencyKey, now,
                    null, null, null, null, 0L, now, now));
            if (candidate.severity().name().equals("URGENT")) {
                enqueueDoctorNotifications(alert, now);
            }
            return alert;
        });
    }

    @Transactional
    public SafetyAlert createTaskOverdueAttention(CareTaskInstance task) {
        String idempotencyKey = "task:" + task.id() + ":overdue";
        return repository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            Instant now = clock.instant();
            return repository.save(new SafetyAlert(
                    0L, task.patientUserId(), "TASK_OVERDUE", com.example.spring.wechat.care.model.SafetySeverity.ATTENTION,
                    SafetyAlertStatus.OPEN, "CARE_TASK", task.id(),
                    "任务“" + task.title() + "”已超时，已再次提醒患者确认。", idempotencyKey, now,
                    null, null, null, null, 0L, now, now));
        });
    }

    @Transactional
    public SafetyAlert escalateTaskOverdue(CareTaskInstance task) {
        String idempotencyKey = "task:" + task.id() + ":overdue";
        Instant now = clock.instant();
        SafetyAlert alert = repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> repository.escalate(existing.id(),
                        "任务“" + task.title() + "”超时超过 30 分钟，已升级为紧急告警。", now))
                .orElseGet(() -> repository.save(new SafetyAlert(
                        0L, task.patientUserId(), "TASK_OVERDUE", com.example.spring.wechat.care.model.SafetySeverity.URGENT,
                        SafetyAlertStatus.ESCALATED, "CARE_TASK", task.id(),
                        "任务“" + task.title() + "”超时超过 30 分钟，已升级为紧急告警。", idempotencyKey, now,
                        null, null, null, null, 0L, now, now)));
        enqueueDoctorNotifications(alert, now);
        return alert;
    }

    public List<SafetyAlert> list(CareActor actor, long patientUserId, int requestedLimit, String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.ALERT_READ,
                "READ_ALERT", "SAFETY_ALERT", null, requestId);
        int limit = requestedLimit <= 0 ? 50 : Math.min(requestedLimit, 100);
        return repository.listByPatient(patientUserId, limit);
    }

    public SafetyAlert acknowledge(CareActor actor, long alertId, ActionCommand command) {
        SafetyAlert alert = find(alertId);
        authorizationService.require(actor, alert.patientUserId(), CarePermissions.ALERT_ACK,
                "ACKNOWLEDGE_ALERT", "SAFETY_ALERT", Long.toString(alertId), command.requestId());
        if (!repository.acknowledge(alertId, actor.userId(), command.version(), command.note(), clock.instant())) {
            throw new CareException(CareErrorCode.CONFLICT, "告警状态已经变化，请刷新后重试");
        }
        return find(alertId);
    }

    public SafetyAlert resolve(CareActor actor, long alertId, ResolveCommand command) {
        SafetyAlert alert = find(alertId);
        authorizationService.require(actor, alert.patientUserId(), CarePermissions.ALERT_ACK,
                "RESOLVE_ALERT", "SAFETY_ALERT", Long.toString(alertId), command.requestId());
        if (!repository.resolve(
                alertId, actor.userId(), command.version(), command.falseAlarm(), command.note(), clock.instant())) {
            throw new CareException(CareErrorCode.CONFLICT, "告警状态已经变化，请刷新后重试");
        }
        return find(alertId);
    }

    private SafetyAlert find(long alertId) {
        return repository.findById(alertId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "安全告警不存在"));
    }

    private void enqueueNotifications(SafetyAlert alert, Instant now) {
        List<NotificationTarget> targets = identityRepository.listNotificationTargets(
                alert.patientUserId(), CarePermissions.ALERT_READ, now);
        for (NotificationTarget target : targets) {
            String content = "患者出现" + alert.severity().name()
                    + "级照护告警，请尽快登录照护端查看。告警编号 #" + alert.id();
            notificationRepository.enqueue(new MedicalNotification(
                    0L, target.userId(), alert.patientUserId(), target.connectionId(), target.recipientId(),
                    "SAFETY_ALERT", "WECHAT", content, "PENDING", now, null, 0,
                    properties.notification().maxRetryCount(), "", null,
                    "alert:" + alert.id() + ":user:" + target.userId(), now, now));
        }
    }

    private void enqueueDoctorNotifications(SafetyAlert alert, Instant now) {
        List<NotificationTarget> targets = identityRepository.listNotificationTargetsByRole(
                alert.patientUserId(), MedicalRole.DOCTOR, CarePermissions.ALERT_READ, now);
        for (NotificationTarget target : targets) {
            String content = "【紧急患者告警】\n患者出现紧急照护情况，请尽快查看。\n告警："
                    + alert.evidenceText() + "\n告警编号 #" + alert.id();
            notificationRepository.enqueue(new MedicalNotification(
                    0L, target.userId(), alert.patientUserId(), target.connectionId(), target.recipientId(),
                    "SAFETY_ALERT_URGENT", "WECHAT", content, "PENDING", now, null, 0,
                    properties.notification().maxRetryCount(), "", null,
                    "urgent-alert:" + alert.id() + ":doctor:" + target.userId(), now, now));
        }
    }

    private String recordValue(HealthRecord record) {
        if (record.secondaryValue() != null) {
            return record.primaryValue() + "/" + record.secondaryValue() + " " + record.unit();
        }
        if (record.primaryValue() != null) {
            return record.primaryValue() + (record.unit().isBlank() ? "" : " " + record.unit());
        }
        return record.recordText();
    }

    public record ActionCommand(long version, String note, String requestId) {
    }

    public record ResolveCommand(long version, boolean falseAlarm, String note, String requestId) {
    }
}
