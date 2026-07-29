package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.PatientStatusSummary;
import com.example.spring.wechat.care.repository.CareRecordRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.care.repository.SafetyAlertRepository;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class CareReportService {

    private final MedicalIdentityRepository identityRepository;
    private final CareRecordRepository recordRepository;
    private final SafetyAlertRepository alertRepository;
    private final CareAuthorizationService authorizationService;
    private final ReminderProperties reminderProperties;
    private final Clock clock;

    public CareReportService(
            MedicalIdentityRepository identityRepository,
            CareRecordRepository recordRepository,
            SafetyAlertRepository alertRepository,
            CareAuthorizationService authorizationService,
            ReminderProperties reminderProperties,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.authorizationService = authorizationService;
        this.reminderProperties = reminderProperties;
        this.clock = clock;
    }

    public List<MedicalUser> listPatients(CareActor actor) {
        return authorizationService.listAccessiblePatients(actor, CarePermissions.STATUS_READ);
    }

    public PatientStatusSummary status(CareActor actor, long patientUserId, String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.STATUS_READ,
                "READ_STATUS", "PATIENT_STATUS", null, requestId);
        MedicalUser patient = identityRepository.findUserById(patientUserId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "患者账号不存在"));
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of(reminderProperties.defaultTimezone())));
        List<DailyCheckIn> checkIns = recordRepository.listCheckIns(patientUserId, today.minusDays(6), today);
        return new PatientStatusSummary(
                patient.id(), patient.userCode(), patient.displayName(), checkIns.size(),
                alertRepository.countOpen(patientUserId), alertRepository.countUrgentOpen(patientUserId),
                recordRepository.countPendingMemories(patientUserId),
                checkIns.stream().map(DailyCheckIn::submittedAt).max(java.time.Instant::compareTo).orElse(null),
                clock.instant());
    }
}
