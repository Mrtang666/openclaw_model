package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CareMemoryEvent;
import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.MemoryEventStatus;
import com.example.spring.wechat.care.model.MemoryVisibility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class CareRecordRepository {

    private static final RowMapper<CareMemoryEvent> MEMORY_MAPPER = (rs, rowNum) -> new CareMemoryEvent(
            rs.getLong("id"), rs.getLong("patient_user_id"), rs.getLong("recorded_by_user_id"),
            rs.getString("original_text"), rs.getString("normalized_text"), instant(rs.getTimestamp("occurred_at")),
            rs.getString("people_json"), rs.getString("place_text"), rs.getString("source_type"),
            rs.getString("source_message_id"), MemoryVisibility.valueOf(rs.getString("visibility")),
            MemoryEventStatus.valueOf(rs.getString("status")), nullableLong(rs, "confirmed_by_user_id"),
            instant(rs.getTimestamp("confirmed_at")), rs.getLong("version"), rs.getString("idempotency_key"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<DailyCheckIn> CHECKIN_MAPPER = (rs, rowNum) -> new DailyCheckIn(
            rs.getLong("id"), rs.getLong("patient_user_id"), rs.getLong("submitted_by_user_id"),
            rs.getDate("checkin_date").toLocalDate(), rs.getString("sleep_status"), rs.getString("meal_status"),
            rs.getString("hydration_status"), rs.getString("mood_status"), rs.getString("activity_status"),
            nullableBoolean(rs, "medication_confirmed"), rs.getString("incident_type"),
            rs.getString("original_text"), rs.getString("source_type"), rs.getString("status"),
            rs.getString("idempotency_key"), rs.getLong("version"), instant(rs.getTimestamp("submitted_at")),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public CareRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CareMemoryEvent saveMemory(CareMemoryEvent event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_memory_events
                    (patient_user_id,recorded_by_user_id,original_text,normalized_text,occurred_at,people_json,
                     place_text,source_type,source_message_id,visibility,status,confirmed_by_user_id,confirmed_at,
                     version,idempotency_key,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, event.patientUserId());
            statement.setLong(2, event.recordedByUserId());
            statement.setString(3, event.originalText());
            statement.setString(4, event.normalizedText());
            statement.setTimestamp(5, nullableTimestamp(event.occurredAt()));
            statement.setString(6, event.peopleJson());
            statement.setString(7, event.placeText());
            statement.setString(8, event.sourceType());
            statement.setString(9, event.sourceMessageId());
            statement.setString(10, event.visibility().name());
            statement.setString(11, event.status().name());
            if (event.confirmedByUserId() == null) statement.setNull(12, java.sql.Types.BIGINT);
            else statement.setLong(12, event.confirmedByUserId());
            statement.setTimestamp(13, nullableTimestamp(event.confirmedAt()));
            statement.setString(14, event.idempotencyKey());
            statement.setTimestamp(15, timestamp(event.createdAt()));
            statement.setTimestamp(16, timestamp(event.updatedAt()));
            return statement;
        }, keyHolder);
        return findMemoryById(requiredKey(keyHolder)).orElseThrow();
    }

    public Optional<CareMemoryEvent> findMemoryById(long memoryId) {
        return jdbc.query("SELECT * FROM medical_memory_events WHERE id=?", MEMORY_MAPPER, memoryId)
                .stream().findFirst();
    }

    public Optional<CareMemoryEvent> findMemoryByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM medical_memory_events WHERE idempotency_key=?", MEMORY_MAPPER, key)
                .stream().findFirst();
    }

    public List<CareMemoryEvent> listMemories(long patientUserId, int limit) {
        return jdbc.query("""
                SELECT * FROM medical_memory_events WHERE patient_user_id=?
                ORDER BY COALESCE(occurred_at,created_at) DESC,id DESC LIMIT ?
                """, MEMORY_MAPPER, patientUserId, limit);
    }

    public boolean updateMemoryStatus(
            long memoryId,
            long expectedVersion,
            MemoryEventStatus status,
            String normalizedText,
            long confirmedByUserId,
            Instant now) {
        return jdbc.update("""
                UPDATE medical_memory_events
                SET status=?,normalized_text=?,confirmed_by_user_id=?,confirmed_at=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('RECEIVED','WAITING_CONFIRMATION')
                """, status.name(), normalizedText, confirmedByUserId, timestamp(now), timestamp(now),
                memoryId, expectedVersion) == 1;
    }

    public DailyCheckIn saveCheckIn(DailyCheckIn checkIn) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_daily_checkins
                    (patient_user_id,submitted_by_user_id,checkin_date,sleep_status,meal_status,hydration_status,
                     mood_status,activity_status,medication_confirmed,incident_type,original_text,source_type,
                     status,idempotency_key,version,submitted_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, checkIn.patientUserId());
            statement.setLong(2, checkIn.submittedByUserId());
            statement.setDate(3, Date.valueOf(checkIn.checkinDate()));
            statement.setString(4, checkIn.sleepStatus());
            statement.setString(5, checkIn.mealStatus());
            statement.setString(6, checkIn.hydrationStatus());
            statement.setString(7, checkIn.moodStatus());
            statement.setString(8, checkIn.activityStatus());
            if (checkIn.medicationConfirmed() == null) statement.setNull(9, java.sql.Types.BOOLEAN);
            else statement.setBoolean(9, checkIn.medicationConfirmed());
            statement.setString(10, checkIn.incidentType());
            statement.setString(11, checkIn.originalText());
            statement.setString(12, checkIn.sourceType());
            statement.setString(13, checkIn.status());
            statement.setString(14, checkIn.idempotencyKey());
            statement.setTimestamp(15, timestamp(checkIn.submittedAt()));
            statement.setTimestamp(16, timestamp(checkIn.createdAt()));
            statement.setTimestamp(17, timestamp(checkIn.updatedAt()));
            return statement;
        }, keyHolder);
        return findCheckInById(requiredKey(keyHolder)).orElseThrow();
    }

    public Optional<DailyCheckIn> findCheckInById(long id) {
        return jdbc.query("SELECT * FROM medical_daily_checkins WHERE id=?", CHECKIN_MAPPER, id)
                .stream().findFirst();
    }

    public Optional<DailyCheckIn> findCheckInByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM medical_daily_checkins WHERE idempotency_key=?", CHECKIN_MAPPER, key)
                .stream().findFirst();
    }

    public Optional<DailyCheckIn> findCheckInByDate(long patientUserId, LocalDate date) {
        return jdbc.query("""
                SELECT * FROM medical_daily_checkins WHERE patient_user_id=? AND checkin_date=?
                """, CHECKIN_MAPPER, patientUserId, Date.valueOf(date)).stream().findFirst();
    }

    public List<DailyCheckIn> listCheckIns(long patientUserId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT * FROM medical_daily_checkins
                WHERE patient_user_id=? AND checkin_date BETWEEN ? AND ?
                ORDER BY checkin_date DESC,id DESC
                """, CHECKIN_MAPPER, patientUserId, Date.valueOf(from), Date.valueOf(to));
    }

    public int countPendingMemories(long patientUserId) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM medical_memory_events
                WHERE patient_user_id=? AND status IN ('RECEIVED','WAITING_CONFIRMATION')
                """, Integer.class, patientUserId);
        return value == null ? 0 : value;
    }

    private static long requiredKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("数据库未返回新增记录编号");
        return key.longValue();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
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
