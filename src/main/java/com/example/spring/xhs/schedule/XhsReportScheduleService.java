package com.example.spring.xhs.schedule;

import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.xhs.report.XhsReportArtifactStorage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class XhsReportScheduleService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> FORMATS = Set.of("DOCX", "XLSX");

    private final JdbcTemplate jdbcTemplate;
    private final EmailProperties emailProperties;
    private final XhsReportArtifactStorage artifactStorage;

    public XhsReportScheduleService(JdbcTemplate jdbcTemplate, EmailProperties emailProperties,
                                    XhsReportArtifactStorage artifactStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailProperties = emailProperties;
        this.artifactStorage = artifactStorage;
    }

    public List<XhsReportScheduleView> schedules(String projectKey) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey.strip();
        List<ScheduleRow> rows = jdbcTemplate.query("""
                SELECT s.*, p.project_key, p.name project_name
                FROM xhs_report_schedules s
                JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY s.enabled DESC, s.next_run_at, s.id DESC
                """, this::mapSchedule, filter, filter);
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public XhsReportScheduleView create(XhsReportScheduleRequest request) {
        Validated value = validate(request);
        long projectId = projectId(value.projectKey());
        Instant now = Instant.now();
        Instant next = value.enabled() ? nextRun(value, now) : null;
        try {
            jdbcTemplate.update("""
                    INSERT INTO xhs_report_schedules(
                        project_id, name, frequency, run_time, day_of_week, day_of_month,
                        timezone, formats, collect_before_report, collection_limit, top_post_limit,
                        enabled, negative_email_enabled, negative_email_minimum_risk_score,
                        negative_email_high_risk_only, negative_email_cooldown_minutes,
                        next_run_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, projectId, value.name(), value.frequency(), Time.valueOf(value.runTime()),
                    value.dayOfWeek(), value.dayOfMonth(), value.timezone().getId(),
                    String.join(",", value.formats()), value.collectBeforeReport(), value.collectionLimit(),
                    value.topPostLimit(), value.enabled(), value.negativeEmailEnabled(), value.negativeEmailMinimumRiskScore(),
                    value.negativeEmailHighRiskOnly(), value.negativeEmailCooldownMinutes(), timestamp(next), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("同一项目下的报告计划名称不能重复", exception);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM xhs_report_schedules WHERE project_id = ? AND name = ?", Long.class,
                projectId, value.name());
        replaceRecipients(id == null ? 0 : id, value, now);
        return schedule(id == null ? 0 : id);
    }

    @Transactional
    public XhsReportScheduleView update(long id, XhsReportScheduleRequest request) {
        Validated value = validate(request);
        long projectId = projectId(value.projectKey());
        Instant now = Instant.now();
        Instant next = value.enabled() ? nextRun(value, now) : null;
        int updated;
        try {
            updated = jdbcTemplate.update("""
                    UPDATE xhs_report_schedules
                    SET project_id = ?, name = ?, frequency = ?, run_time = ?, day_of_week = ?,
                        day_of_month = ?, timezone = ?, formats = ?, collect_before_report = ?,
                        collection_limit = ?, top_post_limit = ?, enabled = ?,
                        negative_email_enabled = ?, negative_email_minimum_risk_score = ?,
                        negative_email_high_risk_only = ?, negative_email_cooldown_minutes = ?,
                        next_run_at = ?, updated_at = ?
                    WHERE id = ?
                    """, projectId, value.name(), value.frequency(), Time.valueOf(value.runTime()),
                    value.dayOfWeek(), value.dayOfMonth(), value.timezone().getId(),
                    String.join(",", value.formats()), value.collectBeforeReport(), value.collectionLimit(),
                    value.topPostLimit(), value.enabled(), value.negativeEmailEnabled(), value.negativeEmailMinimumRiskScore(),
                    value.negativeEmailHighRiskOnly(), value.negativeEmailCooldownMinutes(), timestamp(next), Timestamp.from(now), id);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("同一项目下的报告计划名称不能重复", exception);
        }
        if (updated != 1) {
            throw new IllegalArgumentException("定时报告计划不存在");
        }
        replaceRecipients(id, value, now);
        return schedule(id);
    }

    @Transactional
    public void delete(long id) {
        List<String> storageKeys = jdbcTemplate.query("""
                SELECT a.storage_key FROM xhs_report_artifacts a
                JOIN xhs_report_runs r ON r.id = a.run_id WHERE r.schedule_id = ?
                """, (rs, row) -> rs.getString("storage_key"), id);
        storageKeys.forEach(artifactStorage::delete);
        jdbcTemplate.update("DELETE d FROM xhs_report_deliveries d JOIN xhs_report_runs r ON r.id = d.run_id WHERE r.schedule_id = ?", id);
        jdbcTemplate.update("DELETE a FROM xhs_report_artifacts a JOIN xhs_report_runs r ON r.id = a.run_id WHERE r.schedule_id = ?", id);
        jdbcTemplate.update("DELETE FROM xhs_report_runs WHERE schedule_id = ?", id);
        jdbcTemplate.update("DELETE FROM xhs_report_recipients WHERE schedule_id = ?", id);
        if (jdbcTemplate.update("DELETE FROM xhs_report_schedules WHERE id = ?", id) != 1) {
            throw new IllegalArgumentException("定时报告计划不存在");
        }
    }

    @Transactional
    public long queueNow(long scheduleId) {
        ScheduleRow schedule = row(scheduleId);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return insertRun(schedule, now);
    }

    @Transactional
    public int enqueueDue() {
        Instant now = Instant.now();
        List<ScheduleRow> due = jdbcTemplate.query("""
                SELECT s.*, p.project_key, p.name project_name
                FROM xhs_report_schedules s JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE s.enabled = 1 AND s.next_run_at IS NOT NULL AND s.next_run_at <= ?
                ORDER BY s.next_run_at LIMIT 20
                """, this::mapSchedule, Timestamp.from(now));
        int queued = 0;
        for (ScheduleRow schedule : due) {
            Instant scheduledFor = schedule.nextRunAt();
            try {
                insertRun(schedule, scheduledFor);
                queued++;
            } catch (DuplicateKeyException ignored) {
                // The unique key makes polling and multi-instance retries idempotent.
            }
            Instant next = XhsReportScheduleCalculator.next(
                    schedule.frequency(), schedule.runTime(), schedule.dayOfWeek(), schedule.dayOfMonth(),
                    ZoneId.of(schedule.timezone()), scheduledFor.plusMillis(1));
            jdbcTemplate.update("UPDATE xhs_report_schedules SET last_run_at = ?, next_run_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(scheduledFor), Timestamp.from(next), Timestamp.from(now), schedule.id());
        }
        return queued;
    }

    public List<XhsReportRunView> runs(String projectKey, int limit) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey.strip();
        return jdbcTemplate.query("""
                SELECT r.*, s.name schedule_name, p.project_key, p.name project_name,
                       (SELECT COUNT(*) FROM xhs_report_deliveries d WHERE d.run_id = r.id AND d.status = 'PENDING') pending_deliveries,
                       (SELECT COUNT(*) FROM xhs_report_deliveries d WHERE d.run_id = r.id AND d.status = 'FAILED') failed_deliveries
                FROM xhs_report_runs r
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY r.created_at DESC, r.id DESC LIMIT ?
                """, (rs, rowNumber) -> mapRun(rs), filter, filter, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100)));
    }

    public XhsReportRunView run(long runId) {
        List<XhsReportRunView> values = jdbcTemplate.query("""
                SELECT r.*, s.name schedule_name, p.project_key, p.name project_name,
                       (SELECT COUNT(*) FROM xhs_report_deliveries d WHERE d.run_id = r.id AND d.status = 'PENDING') pending_deliveries,
                       (SELECT COUNT(*) FROM xhs_report_deliveries d WHERE d.run_id = r.id AND d.status = 'FAILED') failed_deliveries
                FROM xhs_report_runs r
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE r.id = ?
                """, (rs, rowNumber) -> mapRun(rs), runId);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("报告运行记录不存在");
        }
        return values.get(0);
    }

    private long insertRun(ScheduleRow schedule, Instant scheduledFor) {
        ZoneId zone = ZoneId.of(schedule.timezone());
        Instant periodEnd = scheduledFor;
        Instant periodStart = XhsReportScheduleCalculator.periodStart(schedule.frequency(), periodEnd, zone);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO xhs_report_runs(schedule_id, scheduled_for, period_start, period_end,
                                            status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'QUEUED', ?, ?)
                """, schedule.id(), Timestamp.from(scheduledFor), Timestamp.from(periodStart),
                Timestamp.from(periodEnd), Timestamp.from(now), Timestamp.from(now));
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM xhs_report_runs WHERE schedule_id = ? AND scheduled_for = ?", Long.class,
                schedule.id(), Timestamp.from(scheduledFor));
        return id == null ? 0 : id;
    }

    private XhsReportRunView mapRun(ResultSet rs) throws SQLException {
        long runId = rs.getLong("id");
        List<XhsReportRunView.Artifact> artifacts = jdbcTemplate.query("""
                SELECT id, format, file_name, size_bytes, created_at
                FROM xhs_report_artifacts WHERE run_id = ? ORDER BY id
                """, (artifactRs, row) -> new XhsReportRunView.Artifact(
                artifactRs.getLong("id"), artifactRs.getString("format"), artifactRs.getString("file_name"),
                artifactRs.getLong("size_bytes"), instant(artifactRs, "created_at")), runId);
        List<XhsReportRunView.Delivery> deliveries = jdbcTemplate.query("""
                SELECT d.id, rr.channel, rr.target_value, d.status, d.attempt_count,
                       d.last_error, d.next_attempt_at, d.sent_at
                FROM xhs_report_deliveries d
                JOIN xhs_report_recipients rr ON rr.id = d.recipient_id
                WHERE d.run_id = ? ORDER BY d.id
                """, (deliveryRs, row) -> new XhsReportRunView.Delivery(
                deliveryRs.getLong("id"), deliveryRs.getString("channel"),
                deliveryRs.getString("target_value"), deliveryRs.getString("status"),
                deliveryRs.getInt("attempt_count"), text(deliveryRs, "last_error"),
                instant(deliveryRs, "next_attempt_at"), instant(deliveryRs, "sent_at")), runId);
        return new XhsReportRunView(
                runId, rs.getLong("schedule_id"), rs.getString("schedule_name"), rs.getString("project_key"),
                rs.getString("project_name"), instant(rs, "scheduled_for"), instant(rs, "period_start"),
                instant(rs, "period_end"), rs.getString("status"), text(rs, "partial_reason"),
                text(rs, "error_message"), instant(rs, "started_at"), instant(rs, "finished_at"),
                instant(rs, "created_at"), artifacts, deliveries, rs.getInt("pending_deliveries"),
                rs.getInt("failed_deliveries"));
    }

    private XhsReportScheduleView schedule(long id) {
        return view(row(id));
    }

    private ScheduleRow row(long id) {
        List<ScheduleRow> values = jdbcTemplate.query("""
                SELECT s.*, p.project_key, p.name project_name
                FROM xhs_report_schedules s JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE s.id = ?
                """, this::mapSchedule, id);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("定时报告计划不存在");
        }
        return values.get(0);
    }

    private ScheduleRow mapSchedule(ResultSet rs, int row) throws SQLException {
        Time time = rs.getTime("run_time");
        return new ScheduleRow(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("project_key"),
                rs.getString("project_name"), rs.getString("name"), rs.getString("frequency"),
                time == null ? LocalTime.of(9, 0) : time.toLocalTime(), nullableInt(rs, "day_of_week"),
                nullableInt(rs, "day_of_month"), rs.getString("timezone"), formats(rs.getString("formats")),
                rs.getBoolean("collect_before_report"), rs.getInt("collection_limit"),
                rs.getInt("top_post_limit"), rs.getBoolean("enabled"), rs.getBoolean("negative_email_enabled"),
                rs.getInt("negative_email_minimum_risk_score"), rs.getBoolean("negative_email_high_risk_only"),
                rs.getInt("negative_email_cooldown_minutes"), instant(rs, "next_run_at"),
                instant(rs, "last_run_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private XhsReportScheduleView view(ScheduleRow row) {
        List<Recipient> recipients = recipients(row.id());
        List<String> emails = recipients.stream().filter(value -> "EMAIL".equals(value.channel()))
                .map(Recipient::target).toList();
        Recipient wechat = recipients.stream().filter(value -> "WECHAT".equals(value.channel())).findFirst().orElse(null);
        return new XhsReportScheduleView(
                row.id(), row.projectKey(), row.projectName(), row.name(), row.frequency(),
                row.runTime().withSecond(0).withNano(0).toString(), row.dayOfWeek(), row.dayOfMonth(),
                row.timezone(), row.formats(), row.collectBeforeReport(), row.collectionLimit(), row.topPostLimit(),
                emails, wechat == null ? "" : wechat.connectionId(), wechat == null ? "" : wechat.target(),
                row.enabled(), row.negativeEmailEnabled(), row.negativeEmailMinimumRiskScore(), row.negativeEmailHighRiskOnly(),
                row.negativeEmailCooldownMinutes(), row.nextRunAt(), row.lastRunAt(), row.createdAt(), row.updatedAt());
    }

    private void replaceRecipients(long scheduleId, Validated value, Instant now) {
        jdbcTemplate.update("UPDATE xhs_report_recipients SET enabled = 0 WHERE schedule_id = ?", scheduleId);
        value.emails().forEach(email -> jdbcTemplate.update("""
                INSERT INTO xhs_report_recipients(schedule_id, channel, connection_id, target_value, enabled, created_at)
                VALUES (?, 'EMAIL', '', ?, 1, ?)
                ON DUPLICATE KEY UPDATE enabled = 1
                """, scheduleId, email, Timestamp.from(now)));
        if (!value.wechatRecipientId().isBlank()) {
            jdbcTemplate.update("""
                    INSERT INTO xhs_report_recipients(schedule_id, channel, connection_id, target_value, enabled, created_at)
                    VALUES (?, 'WECHAT', ?, ?, 1, ?)
                    ON DUPLICATE KEY UPDATE enabled = 1
                    """, scheduleId, value.wechatConnectionId(), value.wechatRecipientId(), Timestamp.from(now));
        }
    }

    private List<Recipient> recipients(long scheduleId) {
        return jdbcTemplate.query("""
                SELECT channel, connection_id, target_value FROM xhs_report_recipients
                WHERE schedule_id = ? AND enabled = 1 ORDER BY id
                """, (rs, row) -> new Recipient(rs.getString("channel"), rs.getString("connection_id"),
                rs.getString("target_value")), scheduleId);
    }

    private Validated validate(XhsReportScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("报告计划参数不能为空");
        }
        String projectKey = required(request.projectKey(), "项目标识");
        String name = required(request.name(), "计划名称");
        String frequency = required(request.frequency(), "执行频率").toUpperCase(Locale.ROOT);
        if (!FREQUENCIES.contains(frequency)) {
            throw new IllegalArgumentException("执行频率只支持 DAILY、WEEKLY 或 MONTHLY");
        }
        LocalTime runTime;
        try {
            runTime = LocalTime.parse(required(request.runTime(), "执行时间"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("执行时间必须使用 HH:mm 格式", exception);
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timezone() == null || request.timezone().isBlank()
                    ? "Asia/Shanghai" : request.timezone().strip());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("无效的时区", exception);
        }
        List<String> formats = normalizeFormats(request.formats());
        List<String> emails = normalizeEmails(request.emailRecipients());
        if (request.negativeEmailEnabled() && emails.isEmpty()) {
            throw new IllegalArgumentException("开启负面帖子即时邮件后至少需要配置一个邮箱接收人");
        }
        String connectionId = safe(request.wechatConnectionId());
        String recipientId = safe(request.wechatRecipientId());
        if (connectionId.isBlank() != recipientId.isBlank()) {
            throw new IllegalArgumentException("微信连接 ID 和接收人 ID 必须同时填写");
        }
        return new Validated(projectKey, name, frequency, runTime,
                frequency.equals("WEEKLY") ? clamp(request.dayOfWeek(), 1, 7, 1) : null,
                frequency.equals("MONTHLY") ? clamp(request.dayOfMonth(), 1, 31, 1) : null,
                zone, formats, request.collectBeforeReport(), clamp(request.collectionLimit(), 1, 100, 20),
                clamp(request.topPostLimit(), 1, 100, 10), emails, connectionId, recipientId, request.enabled(),
                request.negativeEmailEnabled(), clamp(request.negativeEmailMinimumRiskScore(), 0, 100, 60),
                request.negativeEmailHighRiskOnly(), clamp(request.negativeEmailCooldownMinutes(), 1, 1440, 30));
    }

    private List<String> normalizeEmails(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String raw : values) {
            String email = EmailProperties.normalizeEmail(raw);
            if (email.isBlank()) {
                continue;
            }
            if (!EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("邮箱地址格式不正确：" + email);
            }
            if (emailProperties.requireConfirmationForNonWhitelist() && !emailProperties.isAllowedRecipient(email)) {
                throw new IllegalArgumentException("定时发送仅允许 EMAIL_ALLOWED_RECIPIENTS 白名单中的邮箱：" + email);
            }
            result.add(email);
        }
        return List.copyOf(result);
    }

    private List<String> normalizeFormats(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(value -> value != null && !value.isBlank())
                    .map(value -> value.strip().toUpperCase(Locale.ROOT)).forEach(result::add);
        }
        if (result.isEmpty()) {
            result.add("DOCX");
        }
        if (!FORMATS.containsAll(result)) {
            throw new IllegalArgumentException("报告格式只支持 DOCX 和 XLSX");
        }
        return List.copyOf(result);
    }

    private List<String> formats(String value) {
        return value == null || value.isBlank() ? List.of("DOCX")
                : Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isBlank()).toList();
    }

    private Instant nextRun(Validated value, Instant now) {
        return XhsReportScheduleCalculator.next(value.frequency(), value.runTime(), value.dayOfWeek(),
                value.dayOfMonth(), value.timezone(), now);
    }

    private long projectId(String projectKey) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM xhs_monitor_projects WHERE project_key = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong("id"), projectKey);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("未找到启用的项目：" + projectKey);
        }
        return ids.get(0);
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.strip();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private int clamp(Integer value, int minimum, int maximum, int fallback) {
        int actual = value == null || value <= 0 ? fallback : value;
        return Math.max(minimum, Math.min(actual, maximum));
    }

    private record Recipient(String channel, String connectionId, String target) {
    }

    record ScheduleRow(long id, long projectId, String projectKey, String projectName, String name,
                       String frequency, LocalTime runTime, Integer dayOfWeek, Integer dayOfMonth,
                       String timezone, List<String> formats, boolean collectBeforeReport,
                       int collectionLimit, int topPostLimit, boolean enabled, boolean negativeEmailEnabled,
                       int negativeEmailMinimumRiskScore, boolean negativeEmailHighRiskOnly, int negativeEmailCooldownMinutes,
                       Instant nextRunAt,
                       Instant lastRunAt, Instant createdAt, Instant updatedAt) {
    }

    private record Validated(String projectKey, String name, String frequency, LocalTime runTime,
                             Integer dayOfWeek, Integer dayOfMonth, ZoneId timezone, List<String> formats,
                             boolean collectBeforeReport, int collectionLimit, int topPostLimit,
                             List<String> emails, String wechatConnectionId, String wechatRecipientId,
                             boolean enabled, boolean negativeEmailEnabled, int negativeEmailMinimumRiskScore,
                             boolean negativeEmailHighRiskOnly, int negativeEmailCooldownMinutes) {
    }
}
