package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.HealthRecord;
import com.example.spring.wechat.care.model.HealthRecordCategory;
import com.example.spring.wechat.care.model.MedicalRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class HealthRecordRepository {

    private static final RowMapper<HealthRecord> MAPPER = (rs, rowNum) -> new HealthRecord(
            rs.getLong("id"), rs.getLong("patient_user_id"), rs.getLong("recorded_by_user_id"),
            MedicalRole.valueOf(rs.getString("recorder_role")),
            HealthRecordCategory.valueOf(rs.getString("category")),
            rs.getBigDecimal("primary_value"), rs.getBigDecimal("secondary_value"), rs.getString("unit"),
            rs.getString("record_text"), rs.getString("source_type"), instant(rs.getTimestamp("occurred_at")),
            rs.getString("idempotency_key"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public HealthRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HealthRecord save(HealthRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_health_records
                    (patient_user_id,recorded_by_user_id,recorder_role,category,primary_value,secondary_value,
                     unit,record_text,source_type,occurred_at,idempotency_key,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, record.patientUserId());
            statement.setLong(2, record.recordedByUserId());
            statement.setString(3, record.recorderRole().name());
            statement.setString(4, record.category().name());
            statement.setBigDecimal(5, record.primaryValue());
            statement.setBigDecimal(6, record.secondaryValue());
            statement.setString(7, record.unit());
            statement.setString(8, record.recordText());
            statement.setString(9, record.sourceType());
            statement.setTimestamp(10, timestamp(record.occurredAt()));
            statement.setString(11, record.idempotencyKey());
            statement.setTimestamp(12, timestamp(record.createdAt()));
            statement.setTimestamp(13, timestamp(record.updatedAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return health record id");
        return findById(key.longValue()).orElseThrow();
    }

    public Optional<HealthRecord> findById(long id) {
        return jdbc.query("SELECT * FROM medical_health_records WHERE id=?", MAPPER, id).stream().findFirst();
    }

    public Optional<HealthRecord> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM medical_health_records WHERE idempotency_key=?", MAPPER, key)
                .stream().findFirst();
    }

    public List<HealthRecord> listByPatient(long patientUserId, int limit) {
        return jdbc.query("""
                SELECT * FROM medical_health_records WHERE patient_user_id=?
                ORDER BY occurred_at DESC,id DESC LIMIT ?
                """, MAPPER, patientUserId, limit);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
