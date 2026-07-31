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

    @Transactional
    public PatientRelation bindPatientForViewer(CareActor viewer, ViewerBindCommand command) {
        if (viewer == null || viewer.role() == MedicalRole.PATIENT || viewer.role() == MedicalRole.ADMIN) {
            throw new CareException(CareErrorCode.FORBIDDEN, "当前账号不能作为照护方绑定患者");
        }
        if (command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少患者绑定参数");
        }
        MedicalUser patient = identityRepository.findUserByCode(clean(command.patientUserCode()))
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "没有找到患者账号"));
        if (!identityRepository.hasActiveRole(patient.id(), MedicalRole.PATIENT)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "目标账号不是有效患者身份");
        }
        Set<String> permissions = new LinkedHashSet<>(command.permissions() == null
                ? defaultPermissions(viewer.role())
                : command.permissions());
        if (permissions.isEmpty() || !CarePermissions.ALL.containsAll(permissions)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "包含不支持的照护权限");
        }
        Instant now = clock.instant();
        if (command.expiresAt() != null && !command.expiresAt().isAfter(now)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "授权过期时间必须晚于当前时间");
        }
        PatientRelation relation = identityRepository.grantRelation(
                patient.id(), viewer.userId(), viewer.role(), clean(command.relationLabel()),
                Set.copyOf(permissions), command.expiresAt(), now);
        auditRepository.record(viewer, patient.id(), "BIND_PATIENT", null, "PATIENT_RELATION",
                Long.toString(relation.id()), "ALLOWED", "照护方主动绑定患者", requestId(command.requestId()), now);
        return relation;
    }

    @Transactional
    public DoctorTransferResult transferDoctor(CareActor actor, long patientUserId, DoctorTransferCommand command) {
        requireClinicalActor(actor);
        if (command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少医生转移参数");
        }
        require(actor, patientUserId, CarePermissions.PLAN_MANAGE,
                "TRANSFER_DOCTOR", "PATIENT_RELATION", Long.toString(patientUserId), requestId(command.requestId()));
        MedicalUser patient = identityRepository.findUserById(patientUserId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "患者账号不存在"));
        MedicalUser targetDoctor = identityRepository.findUserByCode(clean(command.targetDoctorUserCode()))
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "没有找到目标医生账号"));
        if (targetDoctor.id() == actor.userId()) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "不能把患者转移给当前医生自己");
        }
        if (!identityRepository.hasActiveRole(targetDoctor.id(), MedicalRole.DOCTOR)) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "目标账号不是有效医生身份");
        }
        Instant now = clock.instant();
        PatientRelation newRelation = identityRepository.grantRelation(
                patientUserId,
                targetDoctor.id(),
                MedicalRole.DOCTOR,
                clean(command.relationLabel()).isBlank() ? "转入医生" : clean(command.relationLabel()),
                defaultPermissions(MedicalRole.DOCTOR),
                command.expiresAt(),
                now);
        boolean revoked = identityRepository.revokeRelation(patientUserId, actor.userId(), actor.role(), now);
        if (!revoked) {
            throw new CareException(CareErrorCode.CONFLICT, "当前医生与该患者没有可解除的绑定关系");
        }
        auditRepository.record(actor, patientUserId, "TRANSFER_DOCTOR", CarePermissions.PLAN_MANAGE,
                "PATIENT_RELATION", Long.toString(newRelation.id()), "ALLOWED",
                "患者从当前医生转移给目标医生", requestId(command.requestId()), now);
        return new DoctorTransferResult(patient.id(), patient.userCode(), patient.displayName(),
                actor.userId(), actor.displayName(), targetDoctor.id(), targetDoctor.userCode(),
                targetDoctor.displayName(), newRelation.id());
    }

    @Transactional
    public RelationUnbindResult unbindPatientForViewer(CareActor actor, long patientUserId, String requestId) {
        requireClinicalActor(actor);
        require(actor, patientUserId, CarePermissions.PLAN_MANAGE,
                "UNBIND_PATIENT", "PATIENT_RELATION", Long.toString(patientUserId), requestId(requestId));
        MedicalUser patient = identityRepository.findUserById(patientUserId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "患者账号不存在"));
        Instant now = clock.instant();
        boolean revoked = identityRepository.revokeRelation(patientUserId, actor.userId(), actor.role(), now);
        if (!revoked) {
            throw new CareException(CareErrorCode.CONFLICT, "当前医生与该患者没有可解除的绑定关系");
        }
        auditRepository.record(actor, patientUserId, "UNBIND_PATIENT", CarePermissions.PLAN_MANAGE,
                "PATIENT_RELATION", Long.toString(patientUserId), "ALLOWED",
                "医生主动解除患者绑定", requestId(requestId), now);
        return new RelationUnbindResult(patient.id(), patient.userCode(), patient.displayName(), actor.userId());
    }

    private void requireClinicalActor(CareActor actor) {
        if (actor == null || !actor.role().isClinical()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "只有医生/医护身份可以执行该操作");
        }
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

    public record ViewerBindCommand(
            String patientUserCode,
            String relationLabel,
            Set<String> permissions,
            Instant expiresAt,
            String requestId) {
    }

    public record DoctorTransferCommand(
            String targetDoctorUserCode,
            String relationLabel,
            Instant expiresAt,
            String requestId) {
    }

    public record DoctorTransferResult(
            long patientUserId,
            String patientUserCode,
            String patientDisplayName,
            long fromDoctorUserId,
            String fromDoctorName,
            long toDoctorUserId,
            String toDoctorUserCode,
            String toDoctorName,
            long relationId) {
    }

    public record RelationUnbindResult(
            long patientUserId,
            String patientUserCode,
            String patientDisplayName,
            long unboundUserId) {
    }
}
