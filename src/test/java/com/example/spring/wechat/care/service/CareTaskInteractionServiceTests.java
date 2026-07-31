package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareTaskInteractionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void patientCompletionMarksTheSharedTaskCompleted() {
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CareTaskInteractionService service = service(tasks, authorization, mock(MedicalIdentityRepository.class),
                mock(CareNotificationRepository.class));
        CareTaskInstance task = task(CareTaskStatus.PENDING, 3L);
        CareActor patient = new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT);
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(tasks.complete(9L, 1L, 3L, "微信回复：已完成", NOW)).thenReturn(true);

        CareTaskInteractionService.TaskReplyResult result = service.processReply(patient, "完成 #9", "request-1");

        assertThat(result.stateChanged()).isTrue();
        assertThat(result.message()).contains("已完成");
        verify(authorization).require(patient, 1L, CarePermissions.TASK_UPDATE,
                "COMPLETE_CARE_TASK_BY_WECHAT", "CARE_TASK", "9", "request-1");
    }

    @Test
    void incompleteReplyMarksTaskAbnormalAndQueuesOnlyFamilyRecipients() {
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        MedicalIdentityRepository identities = mock(MedicalIdentityRepository.class);
        CareNotificationRepository notifications = mock(CareNotificationRepository.class);
        CareTaskInteractionService service = service(tasks, authorization, identities, notifications);
        CareTaskInstance task = task(CareTaskStatus.PENDING, 3L);
        CareActor patient = new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT);
        when(tasks.findById(9L)).thenReturn(Optional.of(task));
        when(tasks.reportIncomplete(9L, 1L, 3L, "微信回复：未完成", NOW)).thenReturn(true);
        when(identities.listNotificationTargetsByRole(
                1L, MedicalRole.FAMILY, CarePermissions.TASK_READ, NOW))
                .thenReturn(List.of(new NotificationTarget(2L, "family-connection", "family-recipient")));
        when(identities.listNotificationTargetsByRole(
                1L, MedicalRole.CAREGIVER, CarePermissions.TASK_READ, NOW))
                .thenReturn(List.of());

        CareTaskInteractionService.TaskReplyResult result = service.processReply(patient, "未完成 #9", "request-2");

        ArgumentCaptor<MedicalNotification> notification = ArgumentCaptor.forClass(MedicalNotification.class);
        verify(notifications).enqueue(notification.capture());
        assertThat(notification.getValue().toUserId()).isEqualTo(2L);
        assertThat(notification.getValue().notificationType()).isEqualTo("CARE_TASK_INCOMPLETE");
        assertThat(notification.getValue().content()).contains("晚间服药", "暂未完成");
        assertThat(result.message()).contains("异常", "已通知");
        verify(tasks).markOverdueNotified(9L, NOW);
        verify(identities, never()).listNotificationTargets(eq(1L), any(), any());
    }

    @Test
    void recognisesOnlyAnExplicitTaskNumberToAvoidCompletingTheWrongTask() {
        assertThat(CareTaskInteractionService.looksLikeTaskReply("完成 #9")).isTrue();
        assertThat(CareTaskInteractionService.looksLikeTaskReply("任务9还没完成")).isTrue();
        assertThat(CareTaskInteractionService.looksLikeTaskReply("我完成了今天的事情")).isFalse();
    }

    private CareTaskInteractionService service(
            CareTaskRepository tasks,
            CareAuthorizationService authorization,
            MedicalIdentityRepository identities,
            CareNotificationRepository notifications) {
        return new CareTaskInteractionService(
                tasks, authorization, identities, notifications, new CareProperties("", 12, null),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CareTaskInstance task(CareTaskStatus status, long version) {
        return new CareTaskInstance(
                9L, 8L, 7L, 6L, 1L, "晚间服药", "按医生确认的方案服药", CareTaskType.MEDICATION_CONFIRMATION,
                LocalDate.of(2026, 7, 30), NOW, status, null, null, "", 0,
                NOW, null, "task-9", version, 30, 30, NOW, NOW);
    }
}
