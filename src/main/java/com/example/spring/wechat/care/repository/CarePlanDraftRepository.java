package com.example.spring.wechat.care.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class CarePlanDraftRepository {

    private static final RowMapper<DraftData> MAPPER = (rs, rowNum) -> new DraftData(
            rs.getString("id"),
            rs.getLong("created_by_user_id"),
            rs.getLong("patient_user_id"),
            rs.getString("patient_name"),
            rs.getString("patient_code"),
            rs.getString("title"),
            rs.getString("doctor_input"),
            rs.getString("refined_plan"),
            rs.getString("edited_plan"),
            instant(rs.getTimestamp("confirmed_at")),
            instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public CarePlanDraftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(DraftData draft) {
        jdbc.update("""
                INSERT INTO medical_care_plan_drafts
                (id,created_by_user_id,patient_user_id,patient_name,patient_code,title,
                 doctor_input,refined_plan,edited_plan,confirmed_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                draft.id(), draft.createdByUserId(), draft.patientUserId(), draft.patientName(),
                draft.patientCode(), draft.title(), draft.doctorInput(), draft.refinedPlan(),
                draft.editedPlan(), timestamp(draft.confirmedAt()), timestamp(draft.createdAt()),
                timestamp(draft.updatedAt()));
    }

    public Optional<DraftData> findById(String id) {
        return jdbc.query("SELECT * FROM medical_care_plan_drafts WHERE id=?", MAPPER, id)
                .stream().findFirst();
    }

    public List<DraftData> listByCreator(long userId) {
        return jdbc.query("""
                SELECT * FROM medical_care_plan_drafts
                WHERE created_by_user_id=?
                ORDER BY updated_at DESC
                """, MAPPER, userId);
    }

    @Transactional
    public void update(DraftData draft) {
        int changed = jdbc.update("""
                UPDATE medical_care_plan_drafts
                SET patient_name=?,patient_code=?,title=?,doctor_input=?,refined_plan=?,edited_plan=?,
                    confirmed_at=?,updated_at=?
                WHERE id=?
                """,
                draft.patientName(), draft.patientCode(), draft.title(), draft.doctorInput(),
                draft.refinedPlan(), draft.editedPlan(), timestamp(draft.confirmedAt()),
                timestamp(draft.updatedAt()), draft.id());
        if (changed != 1) {
            throw new IllegalStateException("照护方案草稿不存在：" + draft.id());
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record DraftData(
            String id,
            long createdByUserId,
            long patientUserId,
            String patientName,
            String patientCode,
            String title,
            String doctorInput,
            String refinedPlan,
            String editedPlan,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
