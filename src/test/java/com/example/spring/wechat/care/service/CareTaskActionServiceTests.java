package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.model.CareTaskActionToken;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareTaskActionServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-03T02:00:00Z");

    @Test
    void familyTaskLinkBackfillsTheSharedTaskAndConsumesTheToken() {
        CareTaskActionTokenService tokens = mock(CareTaskActionTokenService.class);
        CareTaskService tasks = mock(CareTaskService.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        MedicalIdentityRepository identities = mock(MedicalIdentityRepository.class);
        CareTaskActionService service = new CareTaskActionService(
                tokens, tasks, authorization, identities, Clock.fixed(NOW, ZoneOffset.UTC));
        CareTaskActionToken token = new CareTaskActionToken(
                7L, 9L, 2L, MedicalRole.FAMILY, "hash", NOW.plusSeconds(1800), null, NOW, NOW);
        CareTaskInstance overdue = task(CareTaskStatus.OVERDUE, 3L, null);
        CareTaskInstance completed = task(CareTaskStatus.COMPLETED, 4L, NOW);
        MedicalUser family = new MedicalUser(2L, "FAM-1", "家属", "ACTIVE", 0L, NOW, NOW, NOW, NOW);
        when(tokens.requireActive("raw-token", NOW)).thenReturn(token);
        when(tasks.findTask(9L)).thenReturn(overdue);
        when(identities.hasActiveRole(2L, MedicalRole.FAMILY)).thenReturn(true);
        when(identities.findUserById(2L)).thenReturn(Optional.of(family));
        when(tasks.backfill(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any())).thenReturn(completed);

        CareTaskActionService.TaskActionView result = service.complete(
                "raw-token", "家属确认完成", "request-1");

        assertThat(result.status()).isEqualTo(CareTaskStatus.COMPLETED);
        verify(authorization).require(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(CarePermissions.PATIENT_TASK_BACKFILL),
                org.mockito.ArgumentMatchers.eq("COMPLETE_TASK_ACTION_LINK"),
                org.mockito.ArgumentMatchers.eq("CARE_TASK"),
                org.mockito.ArgumentMatchers.eq("9"),
                org.mockito.ArgumentMatchers.eq("request-1"));
        verify(tokens).consume(token, NOW);
    }

    private CareTaskInstance task(CareTaskStatus status, long version, Instant completedAt) {
        return new CareTaskInstance(
                9L, 8L, 7L, 6L, 1L, "晚间服药", "晚间服药", CareTaskType.MEDICATION_CONFIRMATION,
                LocalDate.of(2026, 8, 3), NOW.minusSeconds(3600), status,
                completedAt == null ? null : 2L, completedAt, "", 0,
                NOW.minusSeconds(3600), NOW.minusSeconds(1800), null, "task-9", version,
                30, 60, NOW.minusSeconds(7200), NOW);
    }
}
