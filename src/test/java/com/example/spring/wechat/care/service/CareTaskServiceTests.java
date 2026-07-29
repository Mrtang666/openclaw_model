package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareTaskServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void patientCanCompleteOwnPendingTaskWithOptimisticVersion() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CareTaskService service = service(repository, authorization);
        CareActor patient = new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT);
        CareTaskInstance pending = task(CareTaskStatus.PENDING, 3L, null);
        CareTaskInstance completed = task(CareTaskStatus.COMPLETED, 4L, NOW);
        when(repository.findById(9L)).thenReturn(Optional.of(pending), Optional.of(completed));
        when(repository.complete(9L, 1L, 3L, "已经完成", NOW)).thenReturn(true);

        CareTaskInstance result = service.complete(
                patient, 9L, new CareTaskService.ActionCommand(3L, "已经完成", "request-3"));

        assertThat(result.status()).isEqualTo(CareTaskStatus.COMPLETED);
        verify(authorization).require(patient, 1L, CarePermissions.TASK_UPDATE,
                "COMPLETE_CARE_TASK", "CARE_TASK", "9", "request-3");
    }

    @Test
    void postponeCannotExceedConfiguredLimit() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareTaskService service = service(repository, mock(CareAuthorizationService.class));
        CareActor patient = new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT);

        assertThatThrownBy(() -> service.postpone(
                patient, 9L, new CareTaskService.PostponeCommand(3L, 1_441, "", "request-4")))
                .isInstanceOf(CareException.class)
                .extracting(exception -> ((CareException) exception).code())
                .isEqualTo(CareErrorCode.INVALID_ARGUMENT);
    }

    private CareTaskService service(
            CareTaskRepository repository,
            CareAuthorizationService authorization) {
        return new CareTaskService(
                repository, authorization, new CareTaskProperties(true, 60_000, 100, 1, 1_440),
                new ReminderProperties(null, null, "Asia/Shanghai"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CareTaskInstance task(CareTaskStatus status, long version, Instant completedAt) {
        return new CareTaskInstance(
                9L, 8L, 7L, 6L, 1L, "每日签到", "完成签到", CareTaskType.DAILY_CHECKIN,
                LocalDate.of(2026, 7, 29), NOW.minusSeconds(60), status,
                completedAt == null ? null : 1L, completedAt, completedAt == null ? "" : "已经完成",
                0, null, null, "task-9", version, 30, 90, NOW.minusSeconds(3600), NOW);
    }
}
