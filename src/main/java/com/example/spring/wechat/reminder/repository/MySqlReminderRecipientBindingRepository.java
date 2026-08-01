package com.example.spring.wechat.reminder.repository;

import com.example.spring.wechat.reminder.model.ReminderRecipientBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MySqlReminderRecipientBindingRepository implements ReminderRecipientBindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlReminderRecipientBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ReminderRecipientBinding> find(String botId, String recipientId) {
        List<ReminderRecipientBinding> rows = jdbcTemplate.query("""
                        SELECT * FROM reminder_recipient_bindings
                        WHERE bot_id = ? AND recipient_id = ?
                        """,
                (rs, rowNum) -> new ReminderRecipientBinding(
                        rs.getString("bot_id"),
                        rs.getString("recipient_id"),
                        rs.getString("connection_id"),
                        rs.getString("session_key"),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                clean(botId), clean(recipientId));
        return rows.stream().findFirst();
    }

    @Override
    public void upsert(
            String botId,
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant seenAt) {
        Timestamp now = Timestamp.from(seenAt);
        jdbcTemplate.update("""
                        INSERT INTO reminder_recipient_bindings(
                            bot_id, recipient_id, connection_id, session_key,
                            last_seen_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            connection_id = VALUES(connection_id),
                            session_key = VALUES(session_key),
                            last_seen_at = VALUES(last_seen_at),
                            updated_at = VALUES(updated_at)
                        """,
                clean(botId), clean(recipientId), clean(connectionId), clean(sessionKey), now, now, now);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
