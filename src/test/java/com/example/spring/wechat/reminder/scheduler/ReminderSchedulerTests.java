package com.example.spring.wechat.reminder.scheduler;

import com.example.spring.wechat.reminder.config.ReminderProperties;
import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.repository.ReminderTaskRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderSchedulerTests {

    private static final Instant NOW = Instant.parse("2026-07-27T11:30:00Z");

    @Test
    void sendsDueReminderAndSchedulesTheNextDailyOccurrence() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        ReminderNotificationSender sender = mock(ReminderNotificationSender.class);
        ReminderTask task = task(ReminderRepeatType.DAILY, 0, 3);
        when(repository.findDueIds(NOW, 20)).thenReturn(List.of(7L));
        when(repository.claimForDelivery(7L, NOW)).thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(task));

        scheduler(repository, sender).processDue(NOW);

        verify(repository).recordDeliveryStarted(7L, task.nextExecuteAt(), "7:1785151800000", NOW);
        verify(sender).sendText("connection-1", "wechat-user-1", "提醒：取快递\n带上取件码");
        verify(repository).markDelivered(
                7L,
                "7:1785151800000",
                Instant.parse("2026-07-28T11:30:00Z"),
                NOW);
    }

    @Test
    void marksTaskFailedAfterTheLastRetry() {
        ReminderTaskRepository repository = mock(ReminderTaskRepository.class);
        ReminderNotificationSender sender = mock(ReminderNotificationSender.class);
        ReminderTask task = task(ReminderRepeatType.ONCE, 2, 3);
        when(repository.findDueIds(NOW, 20)).thenReturn(List.of(7L));
        when(repository.claimForDelivery(7L, NOW)).thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("connection unavailable"))
                .when(sender).sendText(any(), any(), any());

        scheduler(repository, sender).processDue(NOW);

        verify(repository).markDeliveryFailed(
                eq(7L), eq("7:1785151800000"), eq(null), eq(true),
                eq("connection unavailable"), eq(NOW));
    }

    private ReminderScheduler scheduler(ReminderTaskRepository repository, ReminderNotificationSender sender) {
        return new ReminderScheduler(repository, sender, new ReminderProperties(
                new ReminderProperties.Scheduler(15_000, 20, 300),
                new ReminderProperties.Delivery(3, 60),
                "Asia/Shanghai"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ReminderTask task(ReminderRepeatType repeatType, int retryCount, int maxRetryCount) {
        return new ReminderTask(
                7L,
                null,
                "clawbot:connection-1:wechat-user-1",
                "connection-1",
                "wechat-user-1",
                "取快递",
                "带上取件码",
                repeatType,
                "Asia/Shanghai",
                NOW,
                ReminderStatus.PROCESSING,
                retryCount,
                maxRetryCount,
                NOW,
                "",
                null,
                NOW.minusSeconds(60),
                NOW);
    }
}
