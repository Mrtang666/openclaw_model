package com.example.spring.wechat.reminder.repository;

import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MySqlReminderTaskRepository implements ReminderTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlReminderTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReminderTask save(ReminderTask task) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO reminder_tasks(
                        parent_task_id, session_key, connection_id, recipient_id, title, content, repeat_type, timezone,
                        next_execute_at, status, retry_count, max_retry_count, locked_at, last_error,
                        completed_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            if (task.parentTaskId() == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, task.parentTaskId());
            }
            statement.setString(2, task.sessionKey());
            statement.setString(3, task.connectionId());
            statement.setString(4, task.recipientId());
            statement.setString(5, task.title());
            statement.setString(6, task.content());
            statement.setString(7, task.repeatType().name());
            statement.setString(8, task.timezone());
            statement.setTimestamp(9, timestamp(task.nextExecuteAt()));
            statement.setString(10, task.status().name());
            statement.setInt(11, task.retryCount());
            statement.setInt(12, task.maxRetryCount());
            statement.setTimestamp(13, timestamp(task.lockedAt()));
            statement.setString(14, task.lastError());
            statement.setTimestamp(15, timestamp(task.completedAt()));
            statement.setTimestamp(16, timestamp(task.createdAt()));
            statement.setTimestamp(17, timestamp(task.updatedAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("保存提醒任务失败，未返回任务编号");
        }
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public Optional<ReminderTask> findById(long id) {
        return queryOne("SELECT * FROM reminder_tasks WHERE id = ?", id);
    }

    @Override
    public Optional<ReminderTask> findByIdAndSession(long id, String sessionKey) {
        return queryOne("SELECT * FROM reminder_tasks WHERE id = ? AND session_key = ?", id, clean(sessionKey));
    }

    @Override
    public List<ReminderTask> listBySession(String sessionKey) {
        return listBySession(sessionKey, null, "", 100);
    }

    @Override
    public List<ReminderTask> listBySession(
            String sessionKey, ReminderStatus status, String keyword, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM reminder_tasks
                WHERE session_key = ?
                """);
        List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(clean(sessionKey));
        if (status != null) {
            sql.append(" AND status = ?");
            arguments.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
            String pattern = "%" + keyword.strip() + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        sql.append(" ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, next_execute_at, id DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> map(rs), arguments.toArray());
    }

    @Override
    public Optional<ReminderTask> findLatestDeliveredBySession(String sessionKey) {
        List<ReminderTask> rows = jdbcTemplate.query("""
                        SELECT t.* FROM reminder_tasks t
                        JOIN reminder_deliveries d ON d.task_id = t.id
                        WHERE t.session_key = ? AND d.status = 'SENT'
                        ORDER BY d.sent_at DESC, d.id DESC
                        LIMIT 1
                        """, (rs, rowNum) -> map(rs), clean(sessionKey));
        return rows.stream().findFirst();
    }

    @Override
    public boolean updateActive(
            long id,
            String sessionKey,
            String title,
            String content,
            String timezone,
            Instant nextExecuteAt,
            Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET title = ?, content = ?, timezone = ?, next_execute_at = ?,
                            last_error = NULL, updated_at = ?
                        WHERE id = ? AND session_key = ? AND status = 'ACTIVE'
                        """,
                clean(title), clean(content), clean(timezone), timestamp(nextExecuteAt),
                timestamp(now), id, clean(sessionKey)) > 0;
    }

    @Override
    public int rebindConnection(
            String previousConnectionId,
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET connection_id = ?, session_key = ?, updated_at = ?
                        WHERE connection_id = ? AND recipient_id = ?
                        """,
                clean(connectionId), clean(sessionKey), timestamp(now),
                clean(previousConnectionId), clean(recipientId));
    }

    @Override
    public int adoptSingleKnownConnection(
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET connection_id = ?, session_key = ?, updated_at = ?
                        WHERE recipient_id = ?
                          AND connection_id = (
                              SELECT existing_connection FROM (
                                  SELECT MIN(connection_id) AS existing_connection
                                  FROM reminder_tasks
                                  WHERE recipient_id = ?
                                  HAVING COUNT(DISTINCT connection_id) = 1
                              ) AS single_connection
                          )
                        """,
                clean(connectionId), clean(sessionKey), timestamp(now),
                clean(recipientId), clean(recipientId));
    }

    @Override
    public boolean cancel(long id, String sessionKey, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = 'CANCELLED', next_execute_at = NULL, locked_at = NULL, updated_at = ?
                        WHERE id = ? AND session_key = ? AND status = 'ACTIVE'
                        """, timestamp(now), id, clean(sessionKey)) > 0;
    }

    @Override
    public boolean complete(long id, String sessionKey, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = 'COMPLETED', next_execute_at = NULL, locked_at = NULL,
                            completed_at = ?, updated_at = ?
                        WHERE id = ? AND session_key = ? AND status = 'ACTIVE'
                        """, timestamp(now), timestamp(now), id, clean(sessionKey)) > 0;
    }

    @Override
    public boolean snooze(long id, String sessionKey, Instant nextExecuteAt, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = 'ACTIVE', next_execute_at = ?, locked_at = NULL, last_error = NULL, updated_at = ?
                        WHERE id = ? AND session_key = ? AND status = 'ACTIVE'
                        """, timestamp(nextExecuteAt), timestamp(now), id, clean(sessionKey)) > 0;
    }

    @Override
    public int releaseExpiredLocks(Instant expiredBefore, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = 'ACTIVE', locked_at = NULL, updated_at = ?
                        WHERE status = 'PROCESSING' AND locked_at < ?
                        """, timestamp(now), timestamp(expiredBefore));
    }

    @Override
    public List<Long> findDueIds(Instant now, int limit) {
        return jdbcTemplate.query("""
                        SELECT id FROM reminder_tasks
                        WHERE status = 'ACTIVE' AND next_execute_at <= ?
                        ORDER BY next_execute_at, id
                        LIMIT ?
                        """, (rs, rowNum) -> rs.getLong(1), timestamp(now), limit);
    }

    @Override
    public boolean claimForDelivery(long id, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = 'PROCESSING', locked_at = ?, updated_at = ?
                        WHERE id = ? AND status = 'ACTIVE' AND next_execute_at <= ?
                        """, timestamp(now), timestamp(now), id, timestamp(now)) > 0;
    }

    @Override
    public void recordDeliveryStarted(long taskId, Instant scheduledAt, String idempotencyKey, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO reminder_deliveries(
                            task_id, scheduled_at, idempotency_key, status, error_message, sent_at, created_at, updated_at)
                        VALUES (?, ?, ?, 'PROCESSING', NULL, NULL, ?, ?)
                        ON DUPLICATE KEY UPDATE status = 'PROCESSING', error_message = NULL, updated_at = VALUES(updated_at)
                        """, taskId, timestamp(scheduledAt), clean(idempotencyKey), timestamp(now), timestamp(now));
    }

    @Override
    public void markDelivered(long taskId, String idempotencyKey, Instant nextExecuteAt, Instant now) {
        if (nextExecuteAt == null) {
            jdbcTemplate.update("""
                            UPDATE reminder_tasks
                            SET status = 'COMPLETED', next_execute_at = NULL, locked_at = NULL, retry_count = 0,
                                last_error = NULL, completed_at = ?, updated_at = ?
                            WHERE id = ? AND status = 'PROCESSING'
                            """, timestamp(now), timestamp(now), taskId);
        } else {
            jdbcTemplate.update("""
                            UPDATE reminder_tasks
                            SET status = 'ACTIVE', next_execute_at = ?, locked_at = NULL, retry_count = 0,
                                last_error = NULL, updated_at = ?
                            WHERE id = ? AND status = 'PROCESSING'
                            """, timestamp(nextExecuteAt), timestamp(now), taskId);
        }
        jdbcTemplate.update("""
                        UPDATE reminder_deliveries
                        SET status = 'SENT', error_message = NULL, sent_at = ?, updated_at = ?
                        WHERE idempotency_key = ?
                        """, timestamp(now), timestamp(now), clean(idempotencyKey));
    }

    @Override
    public void markDeliveryFailed(
            long taskId,
            String idempotencyKey,
            Instant retryAt,
            boolean terminal,
            String errorMessage,
            Instant now) {
        String status = terminal ? ReminderStatus.FAILED.name() : ReminderStatus.ACTIVE.name();
        jdbcTemplate.update("""
                        UPDATE reminder_tasks
                        SET status = ?, next_execute_at = ?, locked_at = NULL, retry_count = retry_count + 1,
                            last_error = ?, updated_at = ?
                        WHERE id = ? AND status = 'PROCESSING'
                        """, status, timestamp(retryAt), limit(errorMessage, 512), timestamp(now), taskId);
        jdbcTemplate.update("""
                        UPDATE reminder_deliveries
                        SET status = 'FAILED', error_message = ?, updated_at = ?
                        WHERE idempotency_key = ?
                        """, limit(errorMessage, 512), timestamp(now), clean(idempotencyKey));
    }

    private Optional<ReminderTask> queryOne(String sql, Object... arguments) {
        List<ReminderTask> tasks = jdbcTemplate.query(sql, (rs, rowNum) -> map(rs), arguments);
        return tasks.stream().findFirst();
    }

    private ReminderTask map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReminderTask(
                rs.getLong("id"),
                nullableLong(rs, "parent_task_id"),
                rs.getString("session_key"),
                rs.getString("connection_id"),
                rs.getString("recipient_id"),
                rs.getString("title"),
                rs.getString("content"),
                ReminderRepeatType.valueOf(rs.getString("repeat_type")),
                rs.getString("timezone"),
                instant(rs.getTimestamp("next_execute_at")),
                ReminderStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                instant(rs.getTimestamp("locked_at")),
                rs.getString("last_error"),
                instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int maxLength) {
        String cleanValue = clean(value);
        return cleanValue.length() <= maxLength ? cleanValue : cleanValue.substring(0, maxLength);
    }
}
