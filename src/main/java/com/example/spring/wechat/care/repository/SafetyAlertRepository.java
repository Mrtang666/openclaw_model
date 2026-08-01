package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.SafetyAlert;
import com.example.spring.wechat.care.model.SafetyAlertStatus;
import com.example.spring.wechat.care.model.SafetySeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SafetyAlertRepository {

    private static final RowMapper<SafetyAlert> MAPPER = (rs, rowNum) -> new SafetyAlert(
            rs.getLong("id"), rs.getLong("patient_user_id"), rs.getString("alert_type"),
            SafetySeverity.valueOf(rs.getString("severity")), SafetyAlertStatus.valueOf(rs.getString("status")),
            rs.getString("evidence_type"), nullableLong(rs, "evidence_id"), rs.getString("evidence_text"),
            rs.getString("idempotency_key"), instant(rs.getTimestamp("detected_at")),
            nullableLong(rs, "acknowledged_by_user_id"), instant(rs.getTimestamp("acknowledged_at")),
            nullableLong(rs, "resolved_by_user_id"), instant(rs.getTimestamp("resolved_at")),
            rs.getLong("version"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public SafetyAlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public SafetyAlert save(SafetyAlert alert) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_safety_alerts
                    (patient_user_id,alert_type,severity,status,evidence_type,evidence_id,evidence_text,
                     idempotency_key,detected_at,version,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,0,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, alert.patientUserId());
            statement.setString(2, alert.alertType());
            statement.setString(3, alert.severity().name());
            statement.setString(4, alert.status().name());
            statement.setString(5, alert.evidenceType());
            if (alert.evidenceId() == null) statement.setNull(6, java.sql.Types.BIGINT);
            else statement.setLong(6, alert.evidenceId());
            statement.setString(7, alert.evidenceText());
            statement.setString(8, alert.idempotencyKey());
            statement.setTimestamp(9, timestamp(alert.detectedAt()));
            statement.setTimestamp(10, timestamp(alert.createdAt()));
            statement.setTimestamp(11, timestamp(alert.updatedAt()));
            return statement;
        }, keyHolder);
        long alertId = requiredKey(keyHolder);
        recordEvent(alertId, null, "CREATED", alert.evidenceText(), alert.createdAt());
        return findById(alertId).orElseThrow();
    }

    public Optional<SafetyAlert> findById(long alertId) {
        return jdbc.query("SELECT * FROM medical_safety_alerts WHERE id=?", MAPPER, alertId)
                .stream().findFirst();
    }

    public Optional<SafetyAlert> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM medical_safety_alerts WHERE idempotency_key=?", MAPPER, key)
                .stream().findFirst();
    }

    public List<SafetyAlert> listByPatient(long patientUserId, int limit) {
        return jdbc.query("""
                SELECT * FROM medical_safety_alerts WHERE patient_user_id=?
                ORDER BY detected_at DESC,id DESC LIMIT ?
                """, MAPPER, patientUserId, limit);
    }

    @Transactional
    public boolean acknowledge(long alertId, long actorUserId, long expectedVersion, String note, Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_safety_alerts
                SET status='ACKNOWLEDGED',acknowledged_by_user_id=?,acknowledged_at=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='OPEN'
                """, actorUserId, timestamp(now), timestamp(now), alertId, expectedVersion);
        if (changed == 1) recordEvent(alertId, actorUserId, "ACKNOWLEDGED", note, now);
        return changed == 1;
    }

    @Transactional
    public boolean resolve(
            long alertId,
            long actorUserId,
            long expectedVersion,
            boolean falseAlarm,
            String note,
            Instant now) {
        String target = falseAlarm ? "FALSE_ALARM" : "RESOLVED";
        int changed = jdbc.update("""
                UPDATE medical_safety_alerts
                SET status=?,resolved_by_user_id=?,resolved_at=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('OPEN','ACKNOWLEDGED','ESCALATED')
                """, target, actorUserId, timestamp(now), timestamp(now), alertId, expectedVersion);
        if (changed == 1) recordEvent(alertId, actorUserId, target, note, now);
        return changed == 1;
    }

    public int countOpen(long patientUserId) {
        return count(patientUserId, "status IN ('OPEN','ACKNOWLEDGED','ESCALATED')");
    }

    public int countUrgentOpen(long patientUserId) {
        return count(patientUserId, "severity='URGENT' AND status IN ('OPEN','ACKNOWLEDGED','ESCALATED')");
    }

    private int count(long patientUserId, String condition) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_safety_alerts WHERE patient_user_id=? AND " + condition,
                Integer.class, patientUserId);
        return value == null ? 0 : value;
    }

    private void recordEvent(long alertId, Long actorUserId, String type, String note, Instant now) {
        jdbc.update("""
                INSERT INTO medical_alert_events(alert_id,actor_user_id,event_type,note,created_at)
                VALUES (?,?,?,?,?)
                """, alertId, actorUserId, type, clean(note), timestamp(now));
    }

    private static long requiredKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("数据库未返回新增告警编号");
        return key.longValue();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
