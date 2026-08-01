package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalNotification;
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
            enqueueNotifications(alert, now);
            return alert;
        });
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

    public record ActionCommand(long version, String note, String requestId) {
    }

    public record ResolveCommand(long version, boolean falseAlarm, String note, String requestId) {
    }
}
