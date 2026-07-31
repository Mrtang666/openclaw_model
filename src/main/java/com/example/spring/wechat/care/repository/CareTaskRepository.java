package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.CareTaskType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class CareTaskRepository {

    private static final RowMapper<CareTaskInstance> MAPPER = (rs, rowNum) -> new CareTaskInstance(
            rs.getLong("id"), rs.getLong("plan_id"), rs.getLong("plan_version_id"),
            rs.getLong("task_template_id"), rs.getLong("patient_user_id"),
            rs.getString("task_title"), rs.getString("task_instructions"),
            CareTaskType.valueOf(rs.getString("task_type")), rs.getDate("scheduled_for").toLocalDate(),
            instant(rs.getTimestamp("due_at")), CareTaskStatus.valueOf(rs.getString("status")),
            nullableLong(rs, "completed_by_user_id"), instant(rs.getTimestamp("completed_at")),
            rs.getString("result_note"), rs.getInt("snooze_count"),
            instant(rs.getTimestamp("reminder_enqueued_at")),
            instant(rs.getTimestamp("overdue_notified_at")), rs.getString("idempotency_key"),
            rs.getLong("version"), rs.getInt("grace_period_minutes"),
            rs.getInt("escalation_after_minutes"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private static final String SELECT = """
            SELECT i.*,t.title AS task_title,t.instructions AS task_instructions,t.task_type,
                   t.grace_period_minutes,t.escalation_after_minutes
            FROM medical_care_task_instances i
            JOIN medical_care_task_templates t ON t.id=i.task_template_id
            """;

    private final JdbcTemplate jdbc;

    public CareTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void createInstanceIfAbsent(
            CareTaskTemplate template,
            LocalDate scheduledFor,
            Instant dueAt,
            Instant now) {
        jdbc.update("""
                INSERT IGNORE INTO medical_care_task_instances
                (plan_id,plan_version_id,task_template_id,patient_user_id,scheduled_for,due_at,status,
                 idempotency_key,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',?,0,?,?)
                """, template.planId(), template.planVersionId(), template.id(), template.patientUserId(),
                Date.valueOf(scheduledFor), timestamp(dueAt), instanceKey(template.id(), scheduledFor),
                timestamp(now), timestamp(now));
    }

    public Optional<CareTaskInstance> findById(long taskId) {
        return jdbc.query(SELECT + " WHERE i.id=?", MAPPER, taskId).stream().findFirst();
    }

    public List<CareTaskInstance> listByPatient(long patientUserId, LocalDate from, LocalDate to) {
        return jdbc.query(SELECT + """
                WHERE i.patient_user_id=? AND i.scheduled_for BETWEEN ? AND ?
                ORDER BY i.due_at,i.id
                """, MAPPER, patientUserId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<CareTaskInstance> findReadyForReminder(Instant now, int limit) {
        return jdbc.query(SELECT + """
                WHERE i.status='PENDING' AND i.reminder_enqueued_at IS NULL AND i.due_at<=?
                ORDER BY i.due_at,i.id LIMIT ?
                """, MAPPER, timestamp(now), limit);
    }

    public List<Long> findReadyToMarkOverdue(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT i.id
                FROM medical_care_task_instances i
                JOIN medical_care_task_templates t ON t.id=i.task_template_id
                WHERE i.status='PENDING'
                  AND TIMESTAMPADD(MINUTE,t.grace_period_minutes,i.due_at)<=?
                ORDER BY i.due_at,i.id LIMIT ?
                """, Long.class, timestamp(now), limit);
    }

    public List<CareTaskInstance> findReadyForOverdueNotification(Instant now, int limit) {
        return jdbc.query(SELECT + """
                WHERE i.status='OVERDUE' AND i.overdue_notified_at IS NULL
                  AND TIMESTAMPADD(MINUTE,t.escalation_after_minutes,i.due_at)<=?
                ORDER BY i.due_at,i.id LIMIT ?
                """, MAPPER, timestamp(now), limit);
    }

    public void markReminderEnqueued(long taskId, Instant now) {
        jdbc.update("""
                UPDATE medical_care_task_instances
                SET reminder_enqueued_at=?,updated_at=?
                WHERE id=? AND reminder_enqueued_at IS NULL AND status='PENDING'
                """, timestamp(now), timestamp(now), taskId);
    }

    public void markOverdue(long taskId, Instant now) {
        jdbc.update("""
                UPDATE medical_care_task_instances
                SET status='OVERDUE',version=version+1,updated_at=?
                WHERE id=? AND status='PENDING'
                """, timestamp(now), taskId);
    }

    public void markOverdueNotified(long taskId, Instant now) {
        jdbc.update("""
                UPDATE medical_care_task_instances
                SET overdue_notified_at=?,updated_at=?
                WHERE id=? AND overdue_notified_at IS NULL AND status='OVERDUE'
                """, timestamp(now), timestamp(now), taskId);
    }

    @Transactional
    public boolean complete(long taskId, long actorUserId, long expectedVersion, String note, Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_care_task_instances
                SET status='COMPLETED',completed_by_user_id=?,completed_at=?,result_note=?,
                    version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('PENDING','OVERDUE')
                """, actorUserId, timestamp(now), clean(note), timestamp(now), taskId, expectedVersion);
        if (changed == 1) recordEvent(taskId, actorUserId, "COMPLETED", note, null, null, now);
        return changed == 1;
    }

    /**
     * A patient can explicitly report a task as incomplete before the automatic
     * grace window expires. It is represented as OVERDUE so all existing task
     * views and escalation queries show the same abnormal state.
     */
    @Transactional
    public boolean reportIncomplete(long taskId, long actorUserId, long expectedVersion, String note, Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_care_task_instances
                SET status='OVERDUE',version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('PENDING','OVERDUE')
                """, timestamp(now), taskId, expectedVersion);
        if (changed == 1) {
            recordEvent(taskId, actorUserId, "REPORTED_INCOMPLETE", note, null, null, now);
        }
        return changed == 1;
    }

    @Transactional
    public boolean postpone(
            long taskId,
            long actorUserId,
            long expectedVersion,
            Instant previousDueAt,
            Instant newDueAt,
            String note,
            Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_care_task_instances
                SET status='PENDING',due_at=?,snooze_count=snooze_count+1,reminder_enqueued_at=NULL,
                    overdue_notified_at=NULL,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('PENDING','OVERDUE')
                """, timestamp(newDueAt), timestamp(now), taskId, expectedVersion);
        if (changed == 1) {
            recordEvent(taskId, actorUserId, "POSTPONED", note, previousDueAt, newDueAt, now);
        }
        return changed == 1;
    }

    @Transactional
    public void cancelOpenForPlan(long planId, long actorUserId, String reason, Instant now) {
        List<Long> taskIds = jdbc.queryForList("""
                SELECT id FROM medical_care_task_instances
                WHERE plan_id=? AND status IN ('PENDING','OVERDUE')
                """, Long.class, planId);
        for (Long taskId : taskIds) {
            int changed = jdbc.update("""
                    UPDATE medical_care_task_instances
                    SET status='CANCELLED',version=version+1,updated_at=?
                    WHERE id=? AND status IN ('PENDING','OVERDUE')
                    """, timestamp(now), taskId);
            if (changed == 1) {
                recordEvent(taskId, actorUserId, "CANCELLED", reason, null, null, now);
            }
        }
    }

    @Transactional
    public void reactivateFutureCancelledForPlan(long planId, long actorUserId, Instant now) {
        List<Long> taskIds = jdbc.queryForList("""
                SELECT id FROM medical_care_task_instances
                WHERE plan_id=? AND status='CANCELLED' AND due_at>?
                """, Long.class, planId, timestamp(now));
        for (Long taskId : taskIds) {
            int changed = jdbc.update("""
                    UPDATE medical_care_task_instances
                    SET status='PENDING',reminder_enqueued_at=NULL,overdue_notified_at=NULL,
                        version=version+1,updated_at=?
                    WHERE id=? AND status='CANCELLED' AND due_at>?
                    """, timestamp(now), taskId, timestamp(now));
            if (changed == 1) {
                recordEvent(taskId, actorUserId, "REACTIVATED", "照护计划已恢复", null, null, now);
            }
        }
    }

    private void recordEvent(
            long taskId,
            Long actorUserId,
            String eventType,
            String note,
            Instant previousDueAt,
            Instant currentDueAt,
            Instant now) {
        jdbc.update("""
                INSERT INTO medical_care_task_events
                (task_instance_id,actor_user_id,event_type,note,previous_due_at,current_due_at,created_at)
                VALUES (?,?,?,?,?,?,?)
                """, taskId, actorUserId, eventType, clean(note), nullableTimestamp(previousDueAt),
                nullableTimestamp(currentDueAt), timestamp(now));
    }

    private static String instanceKey(long templateId, LocalDate scheduledFor) {
        return "care-task:" + templateId + ":" + scheduledFor;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Timestamp nullableTimestamp(Instant value) {
        return value == null ? null : timestamp(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
