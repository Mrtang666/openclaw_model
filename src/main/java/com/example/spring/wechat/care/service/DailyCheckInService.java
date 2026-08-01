package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CareRecordRepository;
import com.example.spring.wechat.care.rules.SafetyRuleEngine;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class DailyCheckInService {

    private final CareRecordRepository repository;
    private final SafetyRuleEngine safetyRuleEngine;
    private final SafetyAlertService alertService;
    private final CareAuthorizationService authorizationService;
    private final ReminderProperties reminderProperties;
    private final Clock clock;

    public DailyCheckInService(
            CareRecordRepository repository,
            SafetyRuleEngine safetyRuleEngine,
            SafetyAlertService alertService,
            CareAuthorizationService authorizationService,
            ReminderProperties reminderProperties,
            Clock clock) {
        this.repository = repository;
        this.safetyRuleEngine = safetyRuleEngine;
        this.alertService = alertService;
        this.authorizationService = authorizationService;
        this.reminderProperties = reminderProperties;
        this.clock = clock;
    }

    @Transactional
    public DailyCheckIn submit(CareActor actor, SubmitCommand command) {
        if (actor.role() != MedicalRole.PATIENT) {
            throw new CareException(CareErrorCode.FORBIDDEN, "第一阶段仅允许患者本人提交每日签到");
        }
        if (command == null) throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少签到参数");
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(reminderProperties.defaultTimezone())));
        LocalDate date = command.checkinDate() == null ? today : command.checkinDate();
        if (date.isAfter(today)) throw new CareException(CareErrorCode.INVALID_ARGUMENT, "不能提交未来日期的签到");
        String clientKey = clean(command.idempotencyKey());
        if (clientKey.isBlank()) clientKey = date.toString();
        String finalKey = limit("checkin:" + actor.userId() + ":" + limit(clientKey, 96), 128);
        DailyCheckIn existingByKey = repository.findCheckInByIdempotencyKey(finalKey).orElse(null);
        if (existingByKey != null) return existingByKey;
        if (repository.findCheckInByDate(actor.userId(), date).isPresent()) {
            throw new CareException(CareErrorCode.CONFLICT, "当天已经提交过签到");
        }
        Instant now = clock.instant();
        String incident = limit(clean(command.incidentType()), 64);
        DailyCheckIn saved = repository.saveCheckIn(new DailyCheckIn(
                0L, actor.userId(), actor.userId(), date, value(command.sleepStatus()), value(command.mealStatus()),
                value(command.hydrationStatus()), value(command.moodStatus()), value(command.activityStatus()),
                command.medicationConfirmed(), incident, limit(clean(command.originalText()), 10_000),
                "WECHAT", incident.isBlank() ? "DONE" : "ABNORMAL", finalKey, 0L, now, now, now));
        safetyRuleEngine.evaluate(saved).forEach(candidate ->
                alertService.createFromCheckIn(saved.patientUserId(), saved.id(), candidate));
        return saved;
    }

    public List<DailyCheckIn> list(
            CareActor actor,
            long patientUserId,
            LocalDate from,
            LocalDate to,
            String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.CHECKIN_READ,
                "READ_CHECKIN", "DAILY_CHECKIN", null, requestId);
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end) || start.isBefore(end.minusDays(365))) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "签到查询范围无效或超过一年");
        }
        return repository.listCheckIns(patientUserId, start, end);
    }

    private String value(String text) {
        String value = clean(text).toUpperCase(java.util.Locale.ROOT);
        return limit(value, 32);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record SubmitCommand(
            LocalDate checkinDate,
            String sleepStatus,
            String mealStatus,
            String hydrationStatus,
            String moodStatus,
            String activityStatus,
            Boolean medicationConfirmed,
            String incidentType,
            String originalText,
            String idempotencyKey) {
    }
}
