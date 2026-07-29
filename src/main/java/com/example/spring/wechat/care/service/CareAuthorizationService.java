package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.PatientRelation;
import com.example.spring.wechat.care.repository.CareAuditRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CareAuthorizationService {

    private final MedicalIdentityRepository identityRepository;
    private final CareAuditRepository auditRepository;
    private final Clock clock;

    public CareAuthorizationService(
            MedicalIdentityRepository identityRepository,
            CareAuditRepository auditRepository,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public void require(
            CareActor actor,
            long patientUserId,
            String permission,
            String action,
            String resourceType,
            String resourceId,
            String requestId) {
        Instant now = clock.instant();
        boolean allowed = actor != null && (actor.userId() == patientUserId
                || identityRepository.hasPermission(actor.userId(), patientUserId, permission, now));
        auditRepository.record(actor, patientUserId, action, permission, resourceType, resourceId,
                allowed ? "ALLOWED" : "DENIED", allowed ? "" : "没有患者授权或权限已失效", requestId, now);
        if (!allowed) {
            throw new CareException(CareErrorCode.FORBIDDEN, "没有权限访问该患者数据");
        }
    }

    public List<MedicalUser> listAccessiblePatients(CareActor actor, String permission) {
        if (actor.role() == MedicalRole.PATIENT) {
            return List.of(identityRepository.findUserById(actor.userId())
                    .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "患者账号不存在")));
        }
        return identityRepository.listAccessiblePatients(actor.userId(), permission, clock.instant());
    }

    @Transactional
    public PatientRelation grantRelation(CareActor patient, GrantCommand command) {
        if (patient.role() != MedicalRole.PATIENT) {
            throw new CareException(CareErrorCode.FORBIDDEN, "只有患者账号可以主动授权照护关系");
        }
        if (command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少授权参数");
        }
        MedicalUser viewer = identityRepository.findUserByCode(clean(command.viewerUserCode()))
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "没有找到被授权用户"));
        if (viewer.id() == patient.userId()) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "患者不能把照护关系授权给自己");
        }
        MedicalRole relationRole = parseRole(command.relationRole());
        if (relationRole == MedicalRole.PATIENT || relationRole == MedicalRole.ADMIN) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "关系角色必须是家属、照护人或医护角色");
        }
        if (!identityRepository.hasActiveRole(viewer.id(), relationRole)) {
            throw new CareException(CareErrorCode.CONFLICT, "被授权用户没有对应的有效角色");
        }
        Set<String> permissions = new LinkedHashSet<>(command.permissions() == null
                ? defaultPermissions(relationRole)
                : command.permissions());
        if (permissions.isEmpty() || !CarePermissions.ALL.containsAll(permissions)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "包含不支持的照护权限");
        }
        Instant now = clock.instant();
        if (command.expiresAt() != null && !command.expiresAt().isAfter(now)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "授权过期时间必须晚于当前时间");
        }
        PatientRelation relation = identityRepository.grantRelation(
                patient.userId(), viewer.id(), relationRole, clean(command.relationLabel()),
                Set.copyOf(permissions), command.expiresAt(), now);
        auditRepository.record(patient, patient.userId(), "GRANT_RELATION", null, "PATIENT_RELATION",
                Long.toString(relation.id()), "ALLOWED", "患者主动授权", requestId(command.requestId()), now);
        return relation;
    }

    private Set<String> defaultPermissions(MedicalRole role) {
        if (role.isClinical()) return CarePermissions.ALL;
        if (role.isFamily()) return Set.of(
                CarePermissions.STATUS_READ, CarePermissions.MEMORY_READ, CarePermissions.MEMORY_CONFIRM,
                CarePermissions.CHECKIN_READ, CarePermissions.ALERT_READ, CarePermissions.ALERT_ACK,
                CarePermissions.REPORT_READ, CarePermissions.PLAN_READ, CarePermissions.TASK_READ,
                CarePermissions.TASK_UPDATE);
        return Set.of(CarePermissions.STATUS_READ);
    }

    private MedicalRole parseRole(String value) {
        try {
            return MedicalRole.from(value);
        } catch (IllegalArgumentException exception) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, exception.getMessage());
        }
    }

    private String requestId(String value) {
        return value == null || value.isBlank() ? java.util.UUID.randomUUID().toString() : value.strip();
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public record GrantCommand(
            String viewerUserCode,
            String relationRole,
            String relationLabel,
            Set<String> permissions,
            Instant expiresAt,
            String requestId) {
    }
}
