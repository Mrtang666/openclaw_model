package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class CareDoctorContactService {

    private final MedicalIdentityRepository identityRepository;
    private final CareAuthorizationService authorizationService;
    private final CareNotificationRepository notificationRepository;
    private final ObjectProvider<ReminderNotificationSender> notificationSenderProvider;
    private final Clock clock;

    public CareDoctorContactService(
            MedicalIdentityRepository identityRepository,
            CareAuthorizationService authorizationService,
            CareNotificationRepository notificationRepository,
            ObjectProvider<ReminderNotificationSender> notificationSenderProvider,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.authorizationService = authorizationService;
        this.notificationRepository = notificationRepository;
        this.notificationSenderProvider = notificationSenderProvider;
        this.clock = clock;
    }

    public ContactResult contactDoctor(CareActor sender, long patientUserId, List<Long> doctorUserIds, String message) {
        authorizationService.require(sender, patientUserId, CarePermissions.STATUS_READ,
                "CONTACT_DOCTOR", "PATIENT", Long.toString(patientUserId), java.util.UUID.randomUUID().toString());
        MedicalUser patient = identityRepository.findUserById(patientUserId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "患者账号不存在"));
        List<NotificationTarget> doctors = identityRepository.listNotificationTargetsByRole(
                patientUserId, MedicalRole.DOCTOR, CarePermissions.PLAN_MANAGE, clock.instant());
        if (doctors.isEmpty()) {
            return new ContactResult(0, 0, "当前患者还没有绑定可联系的医生。");
        }
        Set<Long> contactableDoctorIds = new LinkedHashSet<>();
        for (NotificationTarget doctor : doctors) {
            contactableDoctorIds.add(doctor.userId());
        }
        Set<Long> selectedIds = doctorUserIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(doctorUserIds);
        selectedIds.remove(null);
        if (selectedIds.isEmpty() && contactableDoctorIds.size() > 1) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "当前患者绑定了多个医生，请选择具体医生。");
        }
        List<NotificationTarget> targets = selectedIds.isEmpty()
                ? doctors
                : doctors.stream().filter(doctor -> selectedIds.contains(doctor.userId())).toList();
        Set<Long> targetDoctorIds = new LinkedHashSet<>();
        for (NotificationTarget target : targets) {
            targetDoctorIds.add(target.userId());
        }
        if (targets.isEmpty() || (!selectedIds.isEmpty() && !targetDoctorIds.containsAll(selectedIds))) {
            throw new CareException(CareErrorCode.FORBIDDEN, "选择的医生没有绑定该患者，或当前不可联系。");
        }
        String content = """
                【家属联系医生】
                患者：%s（%s）
                发送人：%s（%s）
                内容：%s
                """.formatted(patient.displayName(), patient.userCode(), sender.displayName(), sender.userCode(),
                clean(message, "请医生关注患者当前情况。")).strip();
        int delivered = 0;
        int queued = 0;
        for (NotificationTarget doctor : targets) {
            if (trySendNow(doctor, content)) {
                delivered++;
            } else {
                enqueue(patientUserId, doctor, content);
                queued++;
            }
        }
        return new ContactResult(delivered, queued, "已提交给绑定医生");
    }

    private boolean trySendNow(NotificationTarget target, String content) {
        ReminderNotificationSender sender = notificationSenderProvider.getIfAvailable();
        if (sender == null) {
            return false;
        }
        try {
            sender.sendText(target.connectionId(), target.recipientId(), content);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void enqueue(long patientUserId, NotificationTarget target, String content) {
        Instant now = clock.instant();
        notificationRepository.enqueue(new MedicalNotification(
                0L, target.userId(), patientUserId, target.connectionId(), target.recipientId(),
                "CARE_FAMILY_TO_DOCTOR", "WECHAT", content, "PENDING", now, null, 0,
                3, "", null, idempotency(patientUserId, target.userId(), content), now, now));
    }

    private String idempotency(long patientUserId, long targetUserId, String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (patientUserId + ":" + targetUserId + ":" + content).getBytes(StandardCharsets.UTF_8));
            return "care-doctor-contact:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception exception) {
            return "care-doctor-contact:" + java.util.UUID.randomUUID();
        }
    }

    private String clean(String value, String fallback) {
        String text = value == null ? "" : value.strip();
        return text.isBlank() ? fallback : text;
    }

    public record ContactResult(int deliveredCount, int queuedCount, String message) {
    }
}
