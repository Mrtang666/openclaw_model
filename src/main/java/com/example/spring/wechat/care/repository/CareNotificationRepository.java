package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.MedicalNotification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class CareNotificationRepository {

    private static final RowMapper<MedicalNotification> MAPPER = (rs, rowNum) -> new MedicalNotification(
            rs.getLong("id"), rs.getLong("to_user_id"), nullableLong(rs, "patient_user_id"),
            rs.getString("connection_id"), rs.getString("recipient_id"), rs.getString("notification_type"),
            rs.getString("channel"), rs.getString("content"), rs.getString("status"),
            instant(rs.getTimestamp("scheduled_at")), instant(rs.getTimestamp("sent_at")),
            rs.getInt("retry_count"), rs.getInt("max_retry_count"), rs.getString("last_error"),
            instant(rs.getTimestamp("locked_at")), rs.getString("idempotency_key"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public CareNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(MedicalNotification notification) {
        jdbc.update("""
                INSERT IGNORE INTO medical_notifications
                (to_user_id,patient_user_id,connection_id,recipient_id,notification_type,channel,content,status,
                 scheduled_at,retry_count,max_retry_count,idempotency_key,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,'PENDING',?,0,?,?,?,?)
                """, notification.toUserId(), notification.patientUserId(), notification.connectionId(),
                notification.recipientId(), notification.notificationType(), notification.channel(),
                notification.content(), timestamp(notification.scheduledAt()), notification.maxRetryCount(),
                notification.idempotencyKey(), timestamp(notification.createdAt()), timestamp(notification.updatedAt()));
    }

    public List<Long> findDueIds(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM medical_notifications
                WHERE status='PENDING' AND scheduled_at<=?
                ORDER BY scheduled_at,id LIMIT ?
                """, Long.class, timestamp(now), limit);
    }

    public boolean claim(long id, Instant now) {
        return jdbc.update("""
                UPDATE medical_notifications SET status='PROCESSING',locked_at=?,updated_at=?
                WHERE id=? AND status='PENDING' AND scheduled_at<=?
                """, timestamp(now), timestamp(now), id, timestamp(now)) == 1;
    }

    public Optional<MedicalNotification> findById(long id) {
        return jdbc.query("SELECT * FROM medical_notifications WHERE id=?", MAPPER, id).stream().findFirst();
    }

    public void markSent(long id, Instant now) {
        jdbc.update("""
                UPDATE medical_notifications
                SET status='SENT',sent_at=?,locked_at=NULL,last_error=NULL,updated_at=?
                WHERE id=? AND status='PROCESSING'
                """, timestamp(now), timestamp(now), id);
    }

    public void markFailed(long id, boolean terminal, String error, Instant retryAt, Instant now) {
        jdbc.update("""
                UPDATE medical_notifications
                SET status=?,retry_count=retry_count+1,last_error=?,scheduled_at=COALESCE(?,scheduled_at),
                    locked_at=NULL,updated_at=?
                WHERE id=? AND status='PROCESSING'
                """, terminal ? "FAILED" : "PENDING", limit(error, 1000), nullableTimestamp(retryAt),
                timestamp(now), id);
    }

    public void deferUntilConnectionAvailable(long id, String error, Instant retryAt, Instant now) {
        jdbc.update("""
                UPDATE medical_notifications
                SET status='PENDING',last_error=?,scheduled_at=?,locked_at=NULL,updated_at=?
                WHERE id=? AND status='PROCESSING'
                """, limit(error, 1000), timestamp(retryAt), timestamp(now), id);
    }

    public void requeueConnectionUnavailablePatientNotifications(Instant now) {
        jdbc.update("""
                UPDATE medical_notifications n
                LEFT JOIN medical_care_task_instances i
                    ON n.idempotency_key LIKE CONCAT('task:', i.id, ':%')
                SET n.status='PENDING',n.retry_count=0,n.scheduled_at=?,n.locked_at=NULL,n.updated_at=?
                WHERE n.status='FAILED'
                  AND n.notification_type IN ('CARE_PLAN_TO_PATIENT','CARE_TASK_DUE','CARE_TASK_FOLLOW_UP','CARE_TASK_MISSED')
                  AND (n.last_error LIKE '%微信连接当前不可用%' OR n.last_error LIKE '%没有可用微信连接%')
                  AND (n.notification_type='CARE_PLAN_TO_PATIENT' OR i.status IN ('PENDING','OVERDUE','MISSED'))
                """, timestamp(now), timestamp(now));
    }

    public void releaseExpiredLocks(Instant expiredBefore, Instant now) {
        jdbc.update("""
                UPDATE medical_notifications
                SET status='PENDING',locked_at=NULL,updated_at=?
                WHERE status='PROCESSING' AND locked_at<?
                """, timestamp(now), timestamp(expiredBefore));
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Timestamp nullableTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
