package com.example.spring.wechat.care.scheduler;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareNotificationSchedulerTests {

    @Test
    void failedDeliveryReturnsNotificationToPendingForRetry() {
        CareNotificationRepository repository = mock(CareNotificationRepository.class);
        MedicalIdentityRepository identityRepository = mock(MedicalIdentityRepository.class);
        ReminderNotificationSender sender = mock(ReminderNotificationSender.class);
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        MedicalNotification notification = new MedicalNotification(
                7L, 2L, 1L, "connection", "recipient", "SAFETY_ALERT", "WECHAT", "请查看告警",
                "PROCESSING", now, null, 0, 3, "", now, "notification-7", now, now);
        when(repository.findDueIds(now, 20)).thenReturn(List.of(7L));
        when(repository.claim(7L, now)).thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(notification));
        when(identityRepository.listUserNotificationTargets(2L)).thenReturn(List.of());
        doThrow(new IllegalStateException("send failed"))
                .when(sender).sendText("connection", "recipient", "请查看告警");
        CareProperties properties = new CareProperties("", 12,
                new CareProperties.Notification(true, 15_000, 20, 3, 60, 300));
        CareNotificationScheduler scheduler = new CareNotificationScheduler(
                repository, identityRepository, sender, properties, Clock.fixed(now, ZoneOffset.UTC));

        scheduler.processDue(now);

        verify(repository).markFailed(7L, false, "send failed", now.plusSeconds(60), now);
    }
}
