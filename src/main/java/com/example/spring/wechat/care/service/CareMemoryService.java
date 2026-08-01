package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareMemoryEvent;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MemoryEventStatus;
import com.example.spring.wechat.care.model.MemoryVisibility;
import com.example.spring.wechat.care.repository.CareRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CareMemoryService {

    private final CareRecordRepository repository;
    private final CareAuthorizationService authorizationService;
    private final Clock clock;

    public CareMemoryService(
            CareRecordRepository repository,
            CareAuthorizationService authorizationService,
            Clock clock) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    public CareMemoryEvent record(CareActor actor, RecordCommand command) {
        if (actor.role() != MedicalRole.PATIENT) {
            throw new CareException(CareErrorCode.FORBIDDEN, "第一阶段仅允许患者本人记录记忆");
        }
        if (command == null) throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少记忆记录参数");
        String originalText = required(command.originalText(), "记录内容不能为空");
        String idempotencyKey = idempotency(actor.userId(), command.idempotencyKey(), "memory");
        return repository.findMemoryByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            Instant now = clock.instant();
            CareMemoryEvent event = new CareMemoryEvent(
                    0L, actor.userId(), actor.userId(), limit(originalText, 10_000),
                    limit(clean(command.normalizedText()), 10_000), command.occurredAt(),
                    clean(command.peopleJson()), limit(clean(command.placeText()), 500),
                    "WECHAT", limit(clean(command.sourceMessageId()), 255), parseVisibility(command.visibility()),
                    MemoryEventStatus.WAITING_CONFIRMATION, null, null, 0L, idempotencyKey, now, now);
            return repository.saveMemory(event);
        });
    }

    public List<CareMemoryEvent> list(CareActor actor, long patientUserId, int requestedLimit, String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.MEMORY_READ,
                "READ_MEMORY", "MEMORY_EVENT", null, requestId);
        int limit = requestedLimit <= 0 ? 50 : Math.min(requestedLimit, 100);
        return repository.listMemories(patientUserId, limit).stream()
                .filter(event -> visibleTo(event, actor))
                .toList();
    }

    public CareMemoryEvent confirm(CareActor actor, long memoryId, ConfirmCommand command) {
        if (command == null) throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少记忆确认参数");
        CareMemoryEvent event = repository.findMemoryById(memoryId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "记忆记录不存在"));
        authorizationService.require(actor, event.patientUserId(), CarePermissions.MEMORY_CONFIRM,
                "CONFIRM_MEMORY", "MEMORY_EVENT", Long.toString(memoryId), command.requestId());
        MemoryEventStatus target = parseConfirmationStatus(command.status());
        String normalized = target == MemoryEventStatus.CORRECTED
                ? required(command.correctedText(), "修正记忆时必须提供 correctedText")
                : firstNonBlank(command.correctedText(), event.normalizedText(), event.originalText());
        if (!repository.updateMemoryStatus(
                memoryId, command.version(), target, limit(normalized, 10_000), actor.userId(), clock.instant())) {
            throw new CareException(CareErrorCode.CONFLICT, "记忆状态已经变化，请刷新后重试");
        }
        return repository.findMemoryById(memoryId).orElseThrow();
    }

    private boolean visibleTo(CareMemoryEvent event, CareActor actor) {
        if (actor.userId() == event.patientUserId()) return true;
        if (event.visibility() == MemoryVisibility.PATIENT_ONLY) return false;
        if (event.visibility() == MemoryVisibility.CLINICAL) return actor.role().isClinical();
        return actor.role().isClinical() || actor.role().isFamily();
    }

    private MemoryVisibility parseVisibility(String value) {
        if (value == null || value.isBlank()) return MemoryVisibility.CARE_TEAM;
        try {
            return MemoryVisibility.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT,
                    "visibility 只能是 PATIENT_ONLY、CARE_TEAM 或 CLINICAL");
        }
    }

    private MemoryEventStatus parseConfirmationStatus(String value) {
        String normalized = clean(value).toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "VERIFIED", "CONFIRMED" -> MemoryEventStatus.VERIFIED;
            case "CORRECTED" -> MemoryEventStatus.CORRECTED;
            case "REJECTED" -> MemoryEventStatus.REJECTED;
            default -> throw new CareException(CareErrorCode.INVALID_ARGUMENT,
                    "status 只能是 VERIFIED、CORRECTED 或 REJECTED");
        };
    }

    private String idempotency(long patientUserId, String supplied, String prefix) {
        String value = clean(supplied);
        String clientKey = value.isBlank() ? UUID.randomUUID().toString() : limit(value, 96);
        return limit(prefix + ":" + patientUserId + ":" + clientKey, 128);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }

    private String required(String value, String message) {
        String text = clean(value);
        if (text.isBlank()) throw new CareException(CareErrorCode.INVALID_ARGUMENT, message);
        return text;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record RecordCommand(
            String originalText,
            String normalizedText,
            Instant occurredAt,
            String peopleJson,
            String placeText,
            String visibility,
            String sourceMessageId,
            String idempotencyKey) {
    }

    public record ConfirmCommand(String status, String correctedText, long version, String requestId) {
    }
}
