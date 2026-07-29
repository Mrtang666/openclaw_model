package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CarePlanRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareTaskSchedulerTests {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void weeklyTemplateOnlyMatchesConfiguredWeekday() {
        CareTaskTemplate template = template(CareTaskScheduleType.WEEKLY, null, 3);

        assertThat(CareTaskScheduler.isScheduledFor(template, LocalDate.of(2026, 7, 29))).isTrue();
        assertThat(CareTaskScheduler.isScheduledFor(template, LocalDate.of(2026, 7, 30))).isFalse();
    }

    @Test
    void dueNotificationDoesNotExposeTaskTitleOrInstructions() {
        CarePlanRepository plans = mock(CarePlanRepository.class);
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        MedicalIdentityRepository identities = mock(MedicalIdentityRepository.class);
        CareNotificationRepository notifications = mock(CareNotificationRepository.class);
        CareTaskInstance task = new CareTaskInstance(
                9L, 8L, 7L, 6L, 1L, "晚间服药", "某药物详细内容", CareTaskType.MEDICATION_CONFIRMATION,
                LocalDate.of(2026, 7, 29), NOW, CareTaskStatus.PENDING, null, null, "", 0,
                null, null, "task-9", 0L, 30, 90, NOW, NOW);
        when(plans.listActiveTemplates()).thenReturn(List.of());
        when(tasks.findReadyForReminder(NOW, 100)).thenReturn(List.of(task));
        when(identities.listUserNotificationTargets(1L))
                .thenReturn(List.of(new NotificationTarget(1L, "connection", "recipient")));
        when(tasks.findReadyToMarkOverdue(NOW, 100)).thenReturn(List.of());
        when(tasks.findReadyForOverdueNotification(NOW, 100)).thenReturn(List.of());
        CareTaskScheduler scheduler = new CareTaskScheduler(
                plans, tasks, identities, notifications,
                new CareTaskProperties(true, 60_000, 100, 1, 1_440),
                new CareProperties("", 12, null), Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.process(NOW);

        ArgumentCaptor<MedicalNotification> captor = ArgumentCaptor.forClass(MedicalNotification.class);
        verify(notifications).enqueue(captor.capture());
        assertThat(captor.getValue().content())
                .doesNotContain("晚间服药")
                .doesNotContain("某药物详细内容")
                .contains("任务编号 #9");
        verify(tasks).markReminderEnqueued(9L, NOW);
    }

    private CareTaskTemplate template(
            CareTaskScheduleType schedule,
            LocalDate scheduledDate,
            Integer dayOfWeek) {
        return new CareTaskTemplate(
                6L, 7L, 8L, 1L, CareTaskType.DAILY_CHECKIN, "签到", "完成签到", schedule,
                LocalTime.of(20, 0), scheduledDate, dayOfWeek,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1),
                30, 90, true, 0, "Asia/Shanghai", NOW);
    }
}
