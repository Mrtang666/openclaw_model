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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareTaskServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void listDefaultsToTodayOnly() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CareTaskService service = service(repository, authorization);
        CareActor caregiver = new CareActor(2L, "FAM-1", "family", MedicalRole.CAREGIVER);
        LocalDate today = LocalDate.of(2026, 7, 29);
        when(repository.listByPatient(1L, today, today)).thenReturn(List.of());

        List<CareTaskInstance> result = service.list(caregiver, 1L, null, null, "request-1");

        assertThat(result).isEmpty();
        verify(authorization).require(caregiver, 1L, CarePermissions.TASK_READ,
                "READ_CARE_TASKS", "CARE_TASK", null, "request-1");
        verify(repository).listByPatient(1L, today, today);
    }

    @Test
    void listRemovesDuplicateTaskInstancesAndKeepsMostActionableStatus() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareTaskService service = service(repository, mock(CareAuthorizationService.class));
        CareActor caregiver = new CareActor(2L, "FAM-1", "family", MedicalRole.CAREGIVER);
        LocalDate today = LocalDate.of(2026, 7, 29);
        CareTaskInstance completed = task(
                CareTaskStatus.COMPLETED, 3L, NOW, 9L, NOW.minusSeconds(20),
                "Daily checkin", CareTaskType.DAILY_CHECKIN);
        CareTaskInstance pending = task(
                CareTaskStatus.PENDING, 4L, null, 10L, NOW.minusSeconds(10),
                " Daily   checkin ", CareTaskType.DAILY_CHECKIN);
        CareTaskInstance later = task(
                CareTaskStatus.PENDING, 5L, null, 11L, NOW.plusSeconds(3600),
                "Daily checkin", CareTaskType.DAILY_CHECKIN);
        when(repository.listByPatient(1L, today, today)).thenReturn(List.of(completed, pending, later));

        List<CareTaskInstance> result = service.list(caregiver, 1L, today, today, "request-2");

        assertThat(result).extracting(CareTaskInstance::id).containsExactly(10L, 11L);
        assertThat(result.get(0).status()).isEqualTo(CareTaskStatus.PENDING);
    }

    @Test
    void patientCanCompleteOwnPendingTaskWithOptimisticVersion() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CareTaskService service = service(repository, authorization);
        CareActor patient = new CareActor(1L, "PAT-1", "patient", MedicalRole.PATIENT);
        CareTaskInstance pending = task(CareTaskStatus.PENDING, 3L, null);
        CareTaskInstance completed = task(CareTaskStatus.COMPLETED, 4L, NOW);
        when(repository.findById(9L)).thenReturn(Optional.of(pending), Optional.of(completed));
        when(repository.complete(9L, 1L, 3L, "done", NOW)).thenReturn(true);

        CareTaskInstance result = service.complete(
                patient, 9L, new CareTaskService.ActionCommand(3L, "done", "request-3"));

        assertThat(result.status()).isEqualTo(CareTaskStatus.COMPLETED);
        verify(authorization).require(patient, 1L, CarePermissions.TASK_UPDATE,
                "COMPLETE_CARE_TASK", "CARE_TASK", "9", "request-3");
    }

    @Test
    void postponeCannotExceedConfiguredLimit() {
        CareTaskRepository repository = mock(CareTaskRepository.class);
        CareTaskService service = service(repository, mock(CareAuthorizationService.class));
        CareActor patient = new CareActor(1L, "PAT-1", "patient", MedicalRole.PATIENT);

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
        return task(status, version, completedAt, 9L, NOW.minusSeconds(60),
                "Daily checkin", CareTaskType.DAILY_CHECKIN);
    }

    private CareTaskInstance task(
            CareTaskStatus status,
            long version,
            Instant completedAt,
            long id,
            Instant dueAt,
            String title,
            CareTaskType taskType) {
        return new CareTaskInstance(
                id, 8L, 7L, 6L, 1L, title, "Finish checkin", taskType,
                LocalDate.of(2026, 7, 29), dueAt, status,
                completedAt == null ? null : 1L, completedAt, completedAt == null ? "" : "done",
                0, null, null, "task-" + id, version, 30, 90, NOW.minusSeconds(3600), NOW);
    }
}
