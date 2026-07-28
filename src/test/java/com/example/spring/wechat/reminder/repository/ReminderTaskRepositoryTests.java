package com.example.spring.wechat.reminder.repository;

import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderRecipientBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class ReminderTaskRepositoryTests {

    @Autowired
    private ReminderTaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderRecipientBindingService bindingService;

    @BeforeEach
    void cleanTables() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("openclaw_test");
        jdbcTemplate.update("DELETE FROM reminder_deliveries");
        jdbcTemplate.update("DELETE FROM reminder_tasks");
        jdbcTemplate.update("DELETE FROM reminder_recipient_bindings");
    }

    @Test
    void persistsClaimsAndCompletesAOneTimeReminder() {
        Instant due = Instant.parse("2026-07-27T11:30:00Z");
        ReminderTask saved = repository.save(task(due));

        assertThat(saved.id()).isPositive();
        assertThat(repository.listBySession("clawbot:connection-1:wechat-user-1")).singleElement()
                .extracting(ReminderTask::title).isEqualTo("取快递");
        assertThat(repository.findDueIds(due, 10)).containsExactly(saved.id());
        assertThat(repository.claimForDelivery(saved.id(), due)).isTrue();

        String deliveryKey = saved.id() + ":" + due.toEpochMilli();
        repository.recordDeliveryStarted(saved.id(), due, deliveryKey, due);
        repository.markDelivered(saved.id(), deliveryKey, null, due.plusSeconds(1));

        ReminderTask completed = repository.findById(saved.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(ReminderStatus.COMPLETED);
        assertThat(completed.nextExecuteAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reminder_deliveries WHERE idempotency_key = ?", String.class, deliveryKey))
                .isEqualTo("SENT");
    }

    @Test
    void rebindsExistingRemindersAfterTheUserReconnects() {
        Instant now = Instant.parse("2026-07-27T11:30:00Z");
        ReminderTask saved = repository.save(task(now.plusSeconds(600)));
        bindingService.bind(
                "bot-1", "wechat-user-1", "connection-1",
                "clawbot:connection-1:wechat-user-1", now);

        int migrated = bindingService.bind(
                "bot-1", "wechat-user-1", "connection-2",
                "clawbot:connection-2:wechat-user-1", now.plusSeconds(30));

        assertThat(migrated).isEqualTo(1);
        ReminderTask rebound = repository.findById(saved.id()).orElseThrow();
        assertThat(rebound.connectionId()).isEqualTo("connection-2");
        assertThat(rebound.sessionKey()).isEqualTo("clawbot:connection-2:wechat-user-1");
        assertThat(repository.listBySession("clawbot:connection-2:wechat-user-1"))
                .extracting(ReminderTask::id)
                .containsExactly(saved.id());
    }

    private ReminderTask task(Instant due) {
        return new ReminderTask(
                0L,
                null,
                "clawbot:connection-1:wechat-user-1",
                "connection-1",
                "wechat-user-1",
                "取快递",
                "带上取件码",
                ReminderRepeatType.ONCE,
                "Asia/Shanghai",
                due,
                ReminderStatus.ACTIVE,
                0,
                3,
                null,
                "",
                null,
                due.minusSeconds(60),
                due.minusSeconds(60));
    }
}
