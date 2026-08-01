package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CarePlan;
import com.example.spring.wechat.care.model.CarePlanDetails;
import com.example.spring.wechat.care.model.CarePlanStatus;
import com.example.spring.wechat.care.model.CarePlanType;
import com.example.spring.wechat.care.model.CarePlanVersion;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.CareTaskType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class CarePlanRepository {

    private static final RowMapper<CarePlan> PLAN_MAPPER = (rs, rowNum) -> new CarePlan(
            rs.getLong("id"), rs.getLong("patient_user_id"),
            CarePlanType.valueOf(rs.getString("plan_type")), rs.getString("title"),
            CarePlanStatus.valueOf(rs.getString("status")), rs.getBoolean("clinical_review_required"),
            rs.getInt("current_revision"), rs.getLong("created_by_user_id"),
            instant(rs.getTimestamp("submitted_at")), nullableLong(rs, "reviewed_by_user_id"),
            instant(rs.getTimestamp("reviewed_at")), rs.getString("review_note"),
            instant(rs.getTimestamp("activated_at")), instant(rs.getTimestamp("ended_at")),
            rs.getString("idempotency_key"), rs.getLong("version"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<CarePlanVersion> VERSION_MAPPER = (rs, rowNum) -> new CarePlanVersion(
            rs.getLong("id"), rs.getLong("plan_id"), rs.getInt("revision"),
            rs.getString("summary"), rs.getString("instructions"),
            rs.getDate("effective_from").toLocalDate(), nullableDate(rs, "effective_to"),
            rs.getString("timezone"), rs.getLong("authored_by_user_id"),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<CareTaskTemplate> TEMPLATE_MAPPER = (rs, rowNum) -> new CareTaskTemplate(
            rs.getLong("id"), rs.getLong("plan_version_id"), rs.getLong("plan_id"),
            rs.getLong("patient_user_id"), CareTaskType.valueOf(rs.getString("task_type")),
            rs.getString("title"), rs.getString("instructions"),
            CareTaskScheduleType.valueOf(rs.getString("schedule_type")),
            rs.getTime("local_time").toLocalTime(), nullableDate(rs, "scheduled_date"),
            nullableInteger(rs, "day_of_week"), rs.getDate("start_date").toLocalDate(),
            nullableDate(rs, "end_date"), rs.getInt("follow_up_after_minutes"), rs.getInt("grace_period_minutes"),
            rs.getInt("escalation_after_minutes"), rs.getBoolean("enabled"),
            rs.getInt("sort_order"), rs.getString("timezone"),
            instant(rs.getTimestamp("created_at")));

    private static final String TEMPLATE_SELECT = """
            SELECT t.*,v.plan_id,p.patient_user_id,v.timezone
            FROM medical_care_task_templates t
            JOIN medical_care_plan_versions v ON v.id=t.plan_version_id
            JOIN medical_care_plans p ON p.id=v.plan_id
            """;

    private final JdbcTemplate jdbc;

    public CarePlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public CarePlanDetails create(
            CarePlan plan,
            CarePlanVersion version,
            List<CareTaskTemplate> templates) {
        long planId = insertPlan(plan);
        long versionId = insertVersion(planId, version);
        for (CareTaskTemplate template : templates) {
            insertTemplate(versionId, template);
        }
        return findDetails(planId).orElseThrow();
    }

    public Optional<CarePlan> findById(long planId) {
        return jdbc.query("SELECT * FROM medical_care_plans WHERE id=?", PLAN_MAPPER, planId)
                .stream().findFirst();
    }

    public Optional<CarePlan> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM medical_care_plans WHERE idempotency_key=?", PLAN_MAPPER, key)
                .stream().findFirst();
    }

    public Optional<CarePlanDetails> findDetails(long planId) {
        CarePlan plan = findById(planId).orElse(null);
        if (plan == null) return Optional.empty();
        CarePlanVersion version = jdbc.query("""
                SELECT * FROM medical_care_plan_versions WHERE plan_id=? AND revision=?
                """, VERSION_MAPPER, planId, plan.currentRevision()).stream().findFirst().orElseThrow();
        List<CareTaskTemplate> tasks = jdbc.query(
                TEMPLATE_SELECT + " WHERE t.plan_version_id=? ORDER BY t.sort_order,t.id",
                TEMPLATE_MAPPER, version.id());
        return Optional.of(new CarePlanDetails(plan, version, List.copyOf(tasks)));
    }

    public List<CarePlan> listByPatient(long patientUserId) {
        return jdbc.query("""
                SELECT * FROM medical_care_plans WHERE patient_user_id=?
                ORDER BY FIELD(status,'ACTIVE','APPROVED','WAITING_REVIEW','DRAFT','PAUSED','COMPLETED'),
                    updated_at DESC,id DESC
                """, PLAN_MAPPER, patientUserId);
    }

    public List<CareTaskTemplate> listActiveTemplates() {
        return jdbc.query(TEMPLATE_SELECT + """
                WHERE p.status='ACTIVE' AND p.current_revision=v.revision AND t.enabled=TRUE
                ORDER BY t.id
                """, TEMPLATE_MAPPER);
    }

    @Transactional
    public Optional<CarePlanDetails> revise(
            long planId,
            long expectedVersion,
            CarePlanVersion version,
            List<CareTaskTemplate> templates,
            Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_care_plans
                SET current_revision=current_revision+1,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='DRAFT'
                """, timestamp(now), planId, expectedVersion);
        if (changed != 1) return Optional.empty();
        CarePlan updated = findById(planId).orElseThrow();
        CarePlanVersion next = new CarePlanVersion(
                0L, planId, updated.currentRevision(), version.summary(), version.instructions(),
                version.effectiveFrom(), version.effectiveTo(), version.timezone(),
                version.authoredByUserId(), version.createdAt());
        long versionId = insertVersion(planId, next);
        for (CareTaskTemplate template : templates) insertTemplate(versionId, template);
        return findDetails(planId);
    }

    public boolean submit(long planId, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE medical_care_plans
                SET status='WAITING_REVIEW',submitted_at=?,reviewed_by_user_id=NULL,reviewed_at=NULL,
                    review_note=NULL,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='DRAFT'
                """, timestamp(now), timestamp(now), planId, expectedVersion) == 1;
    }

    public boolean review(
            long planId,
            long reviewerUserId,
            long expectedVersion,
            boolean approved,
            String note,
            Instant now) {
        return jdbc.update("""
                UPDATE medical_care_plans
                SET status=?,reviewed_by_user_id=?,reviewed_at=?,review_note=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='WAITING_REVIEW'
                """, approved ? "APPROVED" : "DRAFT", reviewerUserId, timestamp(now), clean(note),
                timestamp(now), planId, expectedVersion) == 1;
    }

    public boolean activate(long planId, long expectedVersion, Instant now) {
        return jdbc.update("""
                UPDATE medical_care_plans
                SET status='ACTIVE',activated_at=COALESCE(activated_at,?),ended_at=NULL,
                    version=version+1,updated_at=?
                WHERE id=? AND version=? AND status='APPROVED'
                """, timestamp(now), timestamp(now), planId, expectedVersion) == 1;
    }

    public List<Long> completeOtherActivePlans(
            long patientUserId,
            long activePlanId,
            Instant now) {
        List<Long> planIds = jdbc.queryForList("""
                SELECT id
                FROM medical_care_plans
                WHERE patient_user_id=? AND id<>?
                  AND status IN ('ACTIVE','PAUSED')
                """, Long.class, patientUserId, activePlanId);
        if (planIds.isEmpty()) {
            return List.of();
        }
        jdbc.update("""
                UPDATE medical_care_plans
                SET status='COMPLETED',ended_at=?,version=version+1,updated_at=?
                WHERE patient_user_id=? AND id<>?
                  AND status IN ('ACTIVE','PAUSED')
                """, timestamp(now), timestamp(now), patientUserId, activePlanId);
        return List.copyOf(planIds);
    }

    public boolean pause(long planId, long expectedVersion, Instant now) {
        return transition(planId, expectedVersion, "ACTIVE", "PAUSED", now, false);
    }

    public boolean resume(long planId, long expectedVersion, Instant now) {
        return transition(planId, expectedVersion, "PAUSED", "ACTIVE", now, false);
    }

    public boolean complete(long planId, long expectedVersion, Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_care_plans
                SET status='COMPLETED',ended_at=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status IN ('ACTIVE','PAUSED')
                """, timestamp(now), timestamp(now), planId, expectedVersion);
        return changed == 1;
    }

    private boolean transition(
            long planId,
            long expectedVersion,
            String source,
            String target,
            Instant now,
            boolean end) {
        int changed = jdbc.update("""
                UPDATE medical_care_plans
                SET status=?,ended_at=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND status=?
                """, target, end ? timestamp(now) : null, timestamp(now), planId, expectedVersion, source);
        return changed == 1;
    }

    private long insertPlan(CarePlan plan) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_care_plans
                    (patient_user_id,plan_type,title,status,clinical_review_required,current_revision,
                     created_by_user_id,idempotency_key,version,created_at,updated_at)
                    VALUES (?,?,?,'DRAFT',?,1,?,?,0,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, plan.patientUserId());
            statement.setString(2, plan.planType().name());
            statement.setString(3, plan.title());
            statement.setBoolean(4, plan.clinicalReviewRequired());
            statement.setLong(5, plan.createdByUserId());
            statement.setString(6, plan.idempotencyKey());
            statement.setTimestamp(7, timestamp(plan.createdAt()));
            statement.setTimestamp(8, timestamp(plan.updatedAt()));
            return statement;
        }, keys);
        return requiredKey(keys, "照护计划");
    }

    private long insertVersion(long planId, CarePlanVersion version) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_care_plan_versions
                    (plan_id,revision,summary,instructions,effective_from,effective_to,timezone,
                     authored_by_user_id,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, planId);
            statement.setInt(2, version.revision());
            statement.setString(3, version.summary());
            statement.setString(4, version.instructions());
            statement.setDate(5, Date.valueOf(version.effectiveFrom()));
            statement.setDate(6, nullableSqlDate(version.effectiveTo()));
            statement.setString(7, version.timezone());
            statement.setLong(8, version.authoredByUserId());
            statement.setTimestamp(9, timestamp(version.createdAt()));
            return statement;
        }, keys);
        return requiredKey(keys, "照护计划版本");
    }

    private void insertTemplate(long versionId, CareTaskTemplate template) {
        jdbc.update("""
                INSERT INTO medical_care_task_templates
                (plan_version_id,task_type,title,instructions,schedule_type,local_time,scheduled_date,
                 day_of_week,start_date,end_date,follow_up_after_minutes,grace_period_minutes,
                 escalation_after_minutes,enabled,sort_order,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?,?)
                """, versionId, template.taskType().name(), template.title(), template.instructions(),
                template.scheduleType().name(), Time.valueOf(template.localTime()),
                nullableSqlDate(template.scheduledDate()), template.dayOfWeek(),
                Date.valueOf(template.startDate()), nullableSqlDate(template.endDate()),
                template.followUpAfterMinutes(), template.gracePeriodMinutes(), template.escalationAfterMinutes(), template.sortOrder(),
                timestamp(template.createdAt()));
    }

    private static long requiredKey(KeyHolder keyHolder, String resource) {
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("数据库未返回新增" + resource + "编号");
        return key.longValue();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate nullableDate(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Date nullableSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
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
