package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.reminder.config.ReminderProperties;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.repository.ReminderTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReminderServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    @Test
    void createsTaskForTheCurrentWechatConnection() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 42L));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        ReminderTask task = service.create(new ReminderService.CreateCommand(
                "clawbot:connection-1:wechat-user-1",
                "取快递",
                "带上取件码",
                "2026-07-27T19:30:00+08:00",
                "daily",
                "Asia/Shanghai"), NOW);

        assertThat(task.id()).isEqualTo(42L);
        assertThat(task.connectionId()).isEqualTo("connection-1");
        assertThat(task.recipientId()).isEqualTo("wechat-user-1");
        assertThat(task.repeatType()).isEqualTo(ReminderRepeatType.DAILY);
        assertThat(task.nextExecuteAt()).isEqualTo(Instant.parse("2026-07-27T11:30:00Z"));
        assertThat(task.maxRetryCount()).isEqualTo(3);
    }

    @Test
    void rejectsPastTimeAndAConversationWithoutWechatConnection() {
        ReminderService service = new ReminderService(
                mock(ReminderTaskRepository.class), properties(), fixedClock());

        assertThatThrownBy(() -> service.create(new ReminderService.CreateCommand(
                "clawbot:connection-1:wechat-user-1", "开会", "", "2026-07-27T09:00:00Z", "once", ""), NOW))
                .isInstanceOf(ReminderException.class)
                .hasMessageContaining("晚于当前时间");
        assertThatThrownBy(() -> service.create(new ReminderService.CreateCommand(
                "user-1", "开会", "", "2026-07-27T12:00:00Z", "once", ""), NOW))
                .isInstanceOf(ReminderException.class)
                .hasMessageContaining("不支持主动微信提醒");
    }

    @Test
    void createsRelativeReminderFromApplicationClock() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 43L));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        ReminderTask task = service.createAfter(new ReminderService.CreateAfterCommand(
                "clawbot:connection-1:wechat-user-1",
                "喝水",
                "去喝一杯水",
                2,
                "Asia/Shanghai"));

        assertThat(task.id()).isEqualTo(43L);
        assertThat(task.repeatType()).isEqualTo(ReminderRepeatType.ONCE);
        assertThat(task.nextExecuteAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(task.createdAt()).isEqualTo(NOW);
    }

    @Test
    void preservesHoursAsAStrongDelayUnit() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 44L));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        ReminderTask task = service.createAfter(new ReminderService.CreateAfterCommand(
                sessionKey(), "交水费", "", 2L, "hours", "Asia/Shanghai"));

        assertThat(task.nextExecuteAt()).isEqualTo(NOW.plusSeconds(7_200));
    }

    @Test
    void filtersReminderListByStatusKeywordAndLimit() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        ReminderTask active = task(7L, null, "喝水", ReminderStatus.ACTIVE, NOW.plusSeconds(600));
        when(repository.listBySession(sessionKey(), ReminderStatus.ACTIVE, "水", 5))
                .thenReturn(List.of(active));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        List<ReminderTask> tasks = service.list(
                new ReminderService.ListCommand(sessionKey(), "active", "水", 5));

        assertThat(tasks).containsExactly(active);
    }

    @Test
    void refusesToGuessWhenATitleMatchesMultipleActiveReminders() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        when(repository.listBySession(sessionKey(), ReminderStatus.ACTIVE, "喝水", 10))
                .thenReturn(List.of(
                        task(7L, null, "喝水", ReminderStatus.ACTIVE, NOW.plusSeconds(600)),
                        task(8L, null, "喝水", ReminderStatus.ACTIVE, NOW.plusSeconds(900))));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        assertThatThrownBy(() -> service.cancel(
                new ReminderService.TargetCommand(sessionKey(), null, "喝水")))
                .isInstanceOf(ReminderException.class)
                .hasMessageContaining("多个匹配提醒")
                .hasMessageContaining("#7")
                .hasMessageContaining("#8");
    }

    @Test
    void updatesAnActiveReminderByIdWithRelativeHours() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        ReminderTask original = task(7L, null, "交水费", ReminderStatus.ACTIVE, NOW.plusSeconds(600));
        ReminderTask updated = task(7L, null, "交电费", ReminderStatus.ACTIVE, NOW.plusSeconds(7_200));
        when(repository.findByIdAndSession(7L, sessionKey())).thenReturn(Optional.of(original));
        when(repository.updateActive(
                anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(updated));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        ReminderTask result = service.update(new ReminderService.UpdateCommand(
                sessionKey(), 7L, "", "交电费", "", false,
                "", 2L, "hours", ""));

        ArgumentCaptor<Instant> executeAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).updateActive(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(sessionKey()),
                org.mockito.ArgumentMatchers.eq("交电费"),
                org.mockito.ArgumentMatchers.eq("提醒内容"),
                org.mockito.ArgumentMatchers.eq("Asia/Shanghai"),
                executeAt.capture(),
                org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(executeAt.getValue()).isEqualTo(NOW.plusSeconds(7_200));
        assertThat(result.title()).isEqualTo("交电费");
    }

    @Test
    void createsAChildReminderFromTheLatestDelivery() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        ReminderTask delivered = task(7L, null, "喝水", ReminderStatus.COMPLETED, null);
        when(repository.findLatestDeliveredBySession(sessionKey())).thenReturn(Optional.of(delivered));
        when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));
        ReminderService service = new ReminderService(repository, properties(), fixedClock());

        ReminderTask followUp = service.snooze(new ReminderService.SnoozeCommand(
                sessionKey(), null, "", 10L, "minutes"));

        assertThat(followUp.id()).isEqualTo(9L);
        assertThat(followUp.parentTaskId()).isEqualTo(7L);
        assertThat(followUp.repeatType()).isEqualTo(ReminderRepeatType.ONCE);
        assertThat(followUp.nextExecuteAt()).isEqualTo(NOW.plusSeconds(600));
    }

    private ReminderProperties properties() {
        return new ReminderProperties(
                new ReminderProperties.Scheduler(15_000, 20, 300),
                new ReminderProperties.Delivery(3, 60),
                "Asia/Shanghai");
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private String sessionKey() {
        return "clawbot:connection-1:wechat-user-1";
    }

    private ReminderTask task(
            long id,
            Long parentTaskId,
            String title,
            ReminderStatus status,
            Instant nextExecuteAt) {
        return new ReminderTask(
                id, parentTaskId, sessionKey(), "connection-1", "wechat-user-1",
                title, "提醒内容", ReminderRepeatType.ONCE, "Asia/Shanghai", nextExecuteAt,
                status, 0, 3, null, "", status == ReminderStatus.COMPLETED ? NOW : null,
                NOW.minusSeconds(60), NOW);
    }

    private ReminderTask withId(ReminderTask task, long id) {
        return new ReminderTask(
                id, task.parentTaskId(), task.sessionKey(), task.connectionId(), task.recipientId(), task.title(), task.content(),
                task.repeatType(), task.timezone(), task.nextExecuteAt(), ReminderStatus.ACTIVE,
                task.retryCount(), task.maxRetryCount(), task.lockedAt(), task.lastError(), task.completedAt(),
                task.createdAt(), task.updatedAt());
    }
}
