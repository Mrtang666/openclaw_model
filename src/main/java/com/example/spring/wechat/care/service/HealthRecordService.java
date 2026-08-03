package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.HealthRecord;
import com.example.spring.wechat.care.model.HealthRecordCategory;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.SafetyAlert;
import com.example.spring.wechat.care.repository.HealthRecordRepository;
import com.example.spring.wechat.care.rules.HealthRecordRuleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HealthRecordService {

    private final HealthRecordRepository repository;
    private final HealthRecordRuleEngine ruleEngine;
    private final SafetyAlertService alertService;
    private final CareAuthorizationService authorizationService;
    private final Clock clock;

    public HealthRecordService(
            HealthRecordRepository repository,
            HealthRecordRuleEngine ruleEngine,
            SafetyAlertService alertService,
            CareAuthorizationService authorizationService,
            Clock clock) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
        this.alertService = alertService;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @Transactional
    public RecordResult record(CareActor actor, long patientUserId, RecordCommand command, String sourceType) {
        if (actor == null || command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少健康记录参数");
        }
        authorizationService.require(actor, patientUserId, CarePermissions.STATUS_READ,
                "CREATE_HEALTH_RECORD", "HEALTH_RECORD", null, command.requestId());
        HealthRecordCategory category = HealthRecordCategory.from(command.category());
        BigDecimal primary = command.primaryValue();
        BigDecimal secondary = command.secondaryValue();
        String text = limit(clean(command.recordText()), 2000);
        validate(category, primary, secondary, text);
        Instant now = clock.instant();
        Instant occurredAt = command.occurredAt() == null ? now : command.occurredAt();
        if (occurredAt.isAfter(now.plusSeconds(300))) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "健康记录时间不能晚于当前时间");
        }
        String key = idempotency(actor.userId(), patientUserId, command.idempotencyKey());
        HealthRecord saved = repository.findByIdempotencyKey(key).orElseGet(() -> repository.save(new HealthRecord(
                0L, patientUserId, actor.userId(), actor.role(), category, primary, secondary,
                limit(clean(command.unit()), 32), text, clean(sourceType), occurredAt, key, now, now)));
        SafetyAlert alert = null;
        var candidate = ruleEngine.evaluate(saved);
        if (candidate != null) {
            alert = alertService.createFromHealthRecord(saved, candidate);
        }
        return new RecordResult(saved, alert);
    }

    public List<HealthRecord> list(CareActor actor, long patientUserId, int requestedLimit, String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.STATUS_READ,
                "READ_HEALTH_RECORD", "HEALTH_RECORD", null, requestId);
        int limit = requestedLimit <= 0 ? 50 : Math.min(requestedLimit, 100);
        return repository.listByPatient(patientUserId, limit);
    }

    private void validate(HealthRecordCategory category, BigDecimal primary, BigDecimal secondary, String text) {
        if (category == HealthRecordCategory.BLOOD_PRESSURE && (primary == null || secondary == null)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "血压记录需要填写收缩压和舒张压");
        }
        if (category != HealthRecordCategory.BLOOD_PRESSURE && primary == null && text.isBlank()) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "请填写一个数值或文字说明");
        }
        if (primary != null && primary.signum() < 0 || secondary != null && secondary.signum() < 0) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "健康记录数值不能为负数");
        }
    }

    private String idempotency(long actorId, long patientId, String value) {
        String suffix = clean(value);
        if (suffix.isBlank()) suffix = UUID.randomUUID().toString();
        return limit("health:" + actorId + ":" + patientId + ":" + suffix, 128);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record RecordCommand(
            String category,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            String unit,
            String recordText,
            Instant occurredAt,
            String idempotencyKey,
            String requestId) {
    }

    public record RecordResult(HealthRecord record, SafetyAlert alert) {
    }
}
