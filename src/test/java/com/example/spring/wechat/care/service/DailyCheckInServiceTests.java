package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CareRecordRepository;
import com.example.spring.wechat.care.rules.SafetyRuleEngine;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyCheckInServiceTests {

    @Test
    void fallCheckInCreatesSafetyAlert() {
        CareRecordRepository repository = mock(CareRecordRepository.class);
        SafetyAlertService alertService = mock(SafetyAlertService.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
        ReminderProperties reminderProperties = new ReminderProperties(null, null, "Asia/Shanghai");
        DailyCheckInService service = new DailyCheckInService(
                repository, new SafetyRuleEngine(), alertService, authorization, reminderProperties, clock);
        when(repository.findCheckInByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(repository.findCheckInByDate(eq(1L), any())).thenReturn(Optional.empty());
        when(repository.saveCheckIn(any())).thenAnswer(invocation -> {
            DailyCheckIn input = invocation.getArgument(0);
            return new DailyCheckIn(
                    99L, input.patientUserId(), input.submittedByUserId(), input.checkinDate(),
                    input.sleepStatus(), input.mealStatus(), input.hydrationStatus(), input.moodStatus(),
                    input.activityStatus(), input.medicationConfirmed(), input.incidentType(), input.originalText(),
                    input.sourceType(), input.status(), input.idempotencyKey(), input.version(), input.submittedAt(),
                    input.createdAt(), input.updatedAt());
        });

        DailyCheckIn result = service.submit(
                new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT),
                new DailyCheckInService.SubmitCommand(
                        LocalDate.of(2026, 7, 29), "GOOD", "NORMAL", "NORMAL", "CALM", "LOW",
                        true, "FALL", "今天摔倒了", "checkin-1"));

        assertThat(result.status()).isEqualTo("ABNORMAL");
        verify(alertService).createFromCheckIn(eq(1L), eq(99L), any());
    }
}
