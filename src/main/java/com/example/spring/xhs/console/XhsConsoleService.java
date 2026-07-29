package com.example.spring.xhs.console;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.incident.XhsIncidentStatus;
import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.source.XhsCollectionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class XhsConsoleService {

    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private static final List<String> PROJECT_STATUSES = List.of("ACTIVE", "PAUSED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final XhsCollectionCoordinator collectionCoordinator;
    private final XhsDailyReportService dailyReportService;

    public XhsConsoleService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            XhsCollectionCoordinator collectionCoordinator,
            XhsDailyReportService dailyReportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.collectionCoordinator = collectionCoordinator;
        this.dailyReportService = dailyReportService;
    }

    public List<ProjectView> projects() {
        return jdbcTemplate.query("""
                SELECT p.id, p.project_key, p.name, p.status, p.created_at, p.updated_at,
                       COUNT(DISTINCT po.id) post_count,
                       COUNT(DISTINCT CASE WHEN i.status <> 'RESOLVED' THEN i.id END) active_incident_count,
                       GROUP_CONCAT(DISTINCT CASE WHEN t.enabled = 1 THEN t.term_value END
                                    ORDER BY t.id SEPARATOR '\n') terms
                FROM xhs_monitor_projects p
                LEFT JOIN xhs_monitor_terms t ON t.project_id = p.id
                LEFT JOIN xhs_posts po ON po.project_id = p.id
                LEFT JOIN xhs_incidents i ON i.project_id = p.id
                GROUP BY p.id, p.project_key, p.name, p.status, p.created_at, p.updated_at
                ORDER BY p.updated_at DESC, p.id DESC
                """, this::mapProject);
    }

    @Transactional
    public ProjectView createProject(String projectKey, String name, List<String> terms) {
        String key = projectKey(projectKey);
        String projectName = required(name, "项目名称");
        Instant now = Instant.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO xhs_monitor_projects(project_key, name, status, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', ?, ?)
                    """, key, projectName, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("项目标识已存在：" + key, exception);
        }
        long projectId = projectId(key);
        replaceTerms(projectId, terms, now);
        return project(key);
    }

    @Transactional
    public ProjectView updateProject(String projectKey, String name, String status, List<String> terms) {
        String key = projectKey(projectKey);
        ProjectView current = project(key);
        String nextName = name == null ? current.name() : required(name, "项目名称");
        String nextStatus = status == null ? current.status() : normalizeProjectStatus(status);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_monitor_projects SET name = ?, status = ?, updated_at = ?
                WHERE project_key = ?
                """, nextName, nextStatus, Timestamp.from(now), key);
        if (terms != null) {
            replaceTerms(current.id(), terms, now);
        }
        return project(key);
    }

    @Transactional
    public void deleteProject(String projectKey, String confirmation) {
        String key = projectKey(projectKey);
        if (!key.equals(confirmation == null ? "" : confirmation.strip())) {
            throw new IllegalArgumentException("请输入完整项目标识以确认删除");
        }
        long projectId = projectId(key);
        jdbcTemplate.update("""
                DELETE d FROM xhs_alert_deliveries d
                JOIN xhs_alert_events e ON e.id = d.alert_event_id
                JOIN xhs_incidents i ON i.id = e.incident_id
                WHERE i.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE e FROM xhs_alert_events e
                JOIN xhs_incidents i ON i.id = e.incident_id
                WHERE i.project_id = ?
                """, projectId);
        jdbcTemplate.update("DELETE FROM xhs_alert_subscriptions WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM xhs_alert_rules WHERE project_id = ?", projectId);
        jdbcTemplate.update("""
                DELETE a FROM xhs_incident_actions a
                JOIN xhs_incidents i ON i.id = a.incident_id
                WHERE i.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE ip FROM xhs_incident_posts ip
                JOIN xhs_incidents i ON i.id = ip.incident_id
                WHERE i.project_id = ?
                """, projectId);
        jdbcTemplate.update("DELETE FROM xhs_incidents WHERE project_id = ?", projectId);
        jdbcTemplate.update("""
                DELETE a FROM xhs_analysis_results a
                JOIN xhs_posts p ON p.id = a.post_id
                WHERE p.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE c FROM xhs_comments c
                JOIN xhs_posts p ON p.id = c.post_id
                WHERE p.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE m FROM xhs_metric_snapshots m
                JOIN xhs_posts p ON p.id = m.post_id
                WHERE p.project_id = ?
                """, projectId);
        jdbcTemplate.update("DELETE FROM xhs_posts WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM xhs_collection_jobs WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM xhs_source_checkpoints WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM xhs_monitor_terms WHERE project_id = ?", projectId);
        int deleted = jdbcTemplate.update("DELETE FROM xhs_monitor_projects WHERE id = ?", projectId);
        if (deleted != 1) {
            throw new IllegalStateException("项目删除失败，请刷新后重试");
        }
    }

    public OverviewView overview(String projectKey) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(DISTINCT p.id) post_count,
                    COUNT(DISTINCT a.post_id) analyzed_count,
                    COUNT(DISTINCT CASE WHEN a.sentiment = 'NEGATIVE' THEN a.post_id END) negative_count,
                    COUNT(DISTINCT CASE WHEN a.risk_score >= 60 THEN a.post_id END) high_risk_count,
                    COUNT(DISTINCT CASE WHEN i.status <> 'RESOLVED' THEN i.id END) active_incident_count,
                    (SELECT COUNT(*) FROM xhs_collection_jobs j
                     JOIN xhs_monitor_projects jp ON jp.id = j.project_id
                     WHERE j.status = 'FAILED' AND (? IS NULL OR jp.project_key = ?)) failed_job_count
                FROM xhs_monitor_projects pr
                LEFT JOIN xhs_posts p ON p.project_id = pr.id
                LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                LEFT JOIN xhs_incidents i ON i.project_id = pr.id
                WHERE (? IS NULL OR pr.project_key = ?)
                """, (rs, row) -> new OverviewView(
                        rs.getInt("post_count"), rs.getInt("analyzed_count"), rs.getInt("negative_count"),
                        rs.getInt("high_risk_count"), rs.getInt("active_incident_count"),
                        rs.getInt("failed_job_count")), filter, filter, filter, filter);
    }

    public String startCollection(String projectKey, String query, int limit) {
        ProjectView project = project(projectKey(projectKey));
        if (!"ACTIVE".equals(project.status())) {
            throw new IllegalStateException("项目已暂停，不能启动采集");
        }
        String actualQuery = query == null || query.isBlank()
                ? String.join(" ", project.terms())
                : query.strip();
        if (actualQuery.isBlank()) {
            throw new IllegalArgumentException("请先配置项目关键词或输入本次采集关键词");
        }
        return collectionCoordinator.start(new XhsCollectionRequest(
                project.projectKey(), project.name(), actualQuery, limit, ""));
    }

    public List<JobView> jobs(String projectKey, int limit) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        return jdbcTemplate.query("""
                SELECT j.job_key, p.project_key, p.name project_name, j.query_text, j.status,
                       j.complete, j.record_count, j.attempt_count, j.error_code, j.error_message,
                       j.started_at, j.finished_at
                FROM xhs_collection_jobs j
                JOIN xhs_monitor_projects p ON p.id = j.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY j.started_at DESC, j.id DESC
                LIMIT ?
                """, this::mapJob, filter, filter, safeLimit(limit, 50));
    }

    public List<OpinionRow> opinions(
            String projectKey, String keyword, String sentiment, int minimumRiskScore, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, pr.project_key, p.title, p.author_key, p.source_url, p.published_at,
                       a.summary, a.sentiment, a.risk_category, a.risk_score, a.analyzed_at,
                       COALESCE(m.liked_count, 0) liked_count,
                       COALESCE(m.collected_count, 0) collected_count,
                       COALESCE(m.comment_count, 0) comment_count,
                       COALESCE(m.share_count, 0) share_count
                FROM xhs_analysis_results a
                JOIN xhs_posts p ON p.id = a.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                LEFT JOIN xhs_metric_snapshots m ON m.id = (
                    SELECT m2.id FROM xhs_metric_snapshots m2
                    WHERE m2.post_id = p.id ORDER BY m2.snapshot_at DESC, m2.id DESC LIMIT 1)
                WHERE a.risk_score >= ?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(Math.max(0, Math.min(minimumRiskScore, 100)));
        if (projectKey != null && !projectKey.isBlank()) {
            sql.append(" AND pr.project_key = ?");
            arguments.add(projectKey(projectKey));
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.title LIKE ? OR p.content LIKE ? OR a.summary LIKE ?)");
            String like = "%" + keyword.strip() + "%";
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            sql.append(" AND a.sentiment = ?");
            arguments.add(sentiment.strip().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY a.risk_score DESC, a.analyzed_at DESC, p.id DESC LIMIT ?");
        arguments.add(safeLimit(limit, 50));
        return jdbcTemplate.query(sql.toString(), this::mapOpinion, arguments.toArray());
    }

    public PostDetail post(long postId) {
        List<PostDetail> posts = jdbcTemplate.query("""
                SELECT p.id, pr.project_key, p.title, p.content, p.author_key, p.source_url,
                       p.note_type, p.tags_json, p.published_at, p.last_collected_at,
                       a.sentiment, a.sentiment_score, a.risk_category, a.risk_score,
                       a.confidence, a.summary, a.evidence_json, a.explanation_json, a.analyzed_at,
                       COALESCE(m.liked_count, 0) liked_count,
                       COALESCE(m.collected_count, 0) collected_count,
                       COALESCE(m.comment_count, 0) comment_count,
                       COALESCE(m.share_count, 0) share_count
                FROM xhs_posts p
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                LEFT JOIN xhs_analysis_results a ON a.id = (
                    SELECT a2.id FROM xhs_analysis_results a2
                    WHERE a2.post_id = p.id ORDER BY a2.analyzed_at DESC, a2.id DESC LIMIT 1)
                LEFT JOIN xhs_metric_snapshots m ON m.id = (
                    SELECT m2.id FROM xhs_metric_snapshots m2
                    WHERE m2.post_id = p.id ORDER BY m2.snapshot_at DESC, m2.id DESC LIMIT 1)
                WHERE p.id = ?
                """, this::mapPost, postId);
        if (posts.isEmpty()) {
            throw new IllegalArgumentException("帖子不存在");
        }
        PostDetail post = posts.get(0);
        List<CommentView> comments = jdbcTemplate.query("""
                SELECT id, author_key, content, liked_count, published_at
                FROM xhs_comments WHERE post_id = ?
                ORDER BY liked_count DESC, published_at DESC LIMIT 50
                """, (rs, row) -> new CommentView(rs.getLong("id"), anonymousAuthor(rs.getString("author_key")),
                        rs.getString("content"), rs.getLong("liked_count"), instant(rs, "published_at")), postId);
        return post.withComments(comments);
    }

    public List<XhsIncidentView> incidents(String projectKey, String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, p.project_key, i.title, i.risk_category, i.status, i.risk_score,
                       i.risk_level, i.post_count, i.first_seen_at, i.last_seen_at
                FROM xhs_incidents i
                JOIN xhs_monitor_projects p ON p.id = i.project_id
                WHERE 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        if (projectKey != null && !projectKey.isBlank()) {
            sql.append(" AND p.project_key = ?");
            arguments.add(projectKey(projectKey));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND i.status = ?");
            arguments.add(XhsIncidentStatus.from(status).name());
        }
        sql.append(" ORDER BY i.risk_score DESC, i.last_seen_at DESC LIMIT ?");
        arguments.add(safeLimit(limit, 50));
        return jdbcTemplate.query(sql.toString(), (rs, row) -> new XhsIncidentView(
                rs.getLong("id"), rs.getString("project_key"), rs.getString("title"),
                rs.getString("risk_category"), rs.getString("status"), rs.getInt("risk_score"),
                rs.getString("risk_level"), rs.getInt("post_count"), instant(rs, "first_seen_at"),
                instant(rs, "last_seen_at")), arguments.toArray());
    }

    @Transactional
    public IncidentTransitionView transitionIncident(long incidentId, String targetStatus, String note) {
        XhsIncidentStatus target = XhsIncidentStatus.from(targetStatus);
        List<IncidentState> rows = jdbcTemplate.query("""
                SELECT i.status, p.project_key FROM xhs_incidents i
                JOIN xhs_monitor_projects p ON p.id = i.project_id
                WHERE i.id = ? FOR UPDATE
                """, (rs, row) -> new IncidentState(
                        XhsIncidentStatus.from(rs.getString("status")), rs.getString("project_key")), incidentId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("风险事件不存在");
        }
        IncidentState state = rows.get(0);
        if (state.status() == target) {
            return new IncidentTransitionView(incidentId, state.projectKey(), state.status().name(), target.name(), false);
        }
        if (!state.status().canTransitionTo(target)) {
            throw new IllegalStateException("事件状态不能从 " + state.status() + " 变更为 " + target);
        }
        String actionNote = required(note, "处理备注");
        if (actionNote.length() > 1000) {
            throw new IllegalArgumentException("处理备注不能超过 1000 个字符");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE xhs_incidents SET status = ?, updated_at = ? WHERE id = ?",
                target.name(), Timestamp.from(now), incidentId);
        jdbcTemplate.update("""
                INSERT INTO xhs_incident_actions(
                    incident_id, from_status, to_status, actor_connection_id,
                    actor_recipient_id, note, created_at)
                VALUES (?, ?, ?, 'WEB_CONSOLE', 'LOCAL_USER', ?, ?)
                """, incidentId, state.status().name(), target.name(), actionNote, Timestamp.from(now));
        return new IncidentTransitionView(incidentId, state.projectKey(), state.status().name(), target.name(), true);
    }

    public XhsDailyReport dailyReport(String projectKey, String date) {
        return dailyReportService.report(projectKey(projectKey), date, 8);
    }

    public List<AlertRuleView> alertRules(String projectKey) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        return jdbcTemplate.query("""
                SELECT r.id, p.project_key, r.name, r.minimum_risk_score, r.cooldown_minutes,
                       r.enabled, r.created_at, r.updated_at
                FROM xhs_alert_rules r
                JOIN xhs_monitor_projects p ON p.id = r.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY r.updated_at DESC, r.id DESC
                """, (rs, row) -> new AlertRuleView(
                        rs.getLong("id"), rs.getString("project_key"), rs.getString("name"),
                        rs.getInt("minimum_risk_score"), rs.getInt("cooldown_minutes"),
                        rs.getBoolean("enabled"), instant(rs, "created_at"), instant(rs, "updated_at")),
                filter, filter);
    }

    @Transactional
    public AlertRuleView saveAlertRule(
            Long ruleId, String projectKey, String name, int minimumRiskScore, int cooldownMinutes, boolean enabled) {
        String key = projectKey(projectKey);
        long projectId = projectId(key);
        String ruleName = required(name, "规则名称");
        int riskScore = Math.max(0, Math.min(minimumRiskScore, 100));
        int cooldown = Math.max(1, Math.min(cooldownMinutes, 10080));
        Instant now = Instant.now();
        if (ruleId == null) {
            jdbcTemplate.update("""
                    INSERT INTO xhs_alert_rules(project_id, name, minimum_risk_score, cooldown_minutes,
                                                enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, projectId, ruleName, riskScore, cooldown, enabled,
                    Timestamp.from(now), Timestamp.from(now));
            ruleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM xhs_alert_rules WHERE project_id = ? AND name = ?", Long.class,
                    projectId, ruleName);
        } else {
            int updated = jdbcTemplate.update("""
                    UPDATE xhs_alert_rules SET name = ?, minimum_risk_score = ?, cooldown_minutes = ?,
                                               enabled = ?, updated_at = ?
                    WHERE id = ? AND project_id = ?
                    """, ruleName, riskScore, cooldown, enabled, Timestamp.from(now), ruleId, projectId);
            if (updated != 1) {
                throw new IllegalArgumentException("告警规则不存在");
            }
        }
        long id = ruleId;
        return alertRules(key).stream().filter(rule -> rule.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("告警规则保存失败"));
    }

    public List<AlertEventView> alertEvents(String projectKey, int limit) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        return jdbcTemplate.query("""
                SELECT e.id, p.project_key, r.name rule_name, i.id incident_id, i.title,
                       e.status, e.risk_score, e.sent_at, e.acknowledged_at, e.created_at
                FROM xhs_alert_events e
                JOIN xhs_alert_rules r ON r.id = e.rule_id
                JOIN xhs_incidents i ON i.id = e.incident_id
                JOIN xhs_monitor_projects p ON p.id = i.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY e.created_at DESC, e.id DESC LIMIT ?
                """, (rs, row) -> new AlertEventView(
                        rs.getLong("id"), rs.getString("project_key"), rs.getString("rule_name"),
                        rs.getLong("incident_id"), rs.getString("title"), rs.getString("status"),
                        rs.getInt("risk_score"), instant(rs, "sent_at"), instant(rs, "acknowledged_at"),
                instant(rs, "created_at")), filter, filter, safeLimit(limit, 50));
    }

    public AlertEventView acknowledgeAlertEvent(long eventId) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE xhs_alert_events
                SET status = 'ACKNOWLEDGED', acknowledged_at = ?
                WHERE id = ? AND status <> 'ACKNOWLEDGED'
                """, Timestamp.from(now), eventId);
        List<AlertEventView> events = jdbcTemplate.query("""
                SELECT e.id, p.project_key, r.name rule_name, i.id incident_id, i.title,
                       e.status, e.risk_score, e.sent_at, e.acknowledged_at, e.created_at
                FROM xhs_alert_events e
                JOIN xhs_alert_rules r ON r.id = e.rule_id
                JOIN xhs_incidents i ON i.id = e.incident_id
                JOIN xhs_monitor_projects p ON p.id = i.project_id
                WHERE e.id = ?
                """, (rs, row) -> new AlertEventView(
                        rs.getLong("id"), rs.getString("project_key"), rs.getString("rule_name"),
                        rs.getLong("incident_id"), rs.getString("title"), rs.getString("status"),
                        rs.getInt("risk_score"), instant(rs, "sent_at"), instant(rs, "acknowledged_at"),
                        instant(rs, "created_at")), eventId);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("告警事件不存在");
        }
        if (updated == 0 && !"ACKNOWLEDGED".equals(events.get(0).status())) {
            throw new IllegalStateException("告警事件状态已变化，请刷新后重试");
        }
        return events.get(0);
    }

    private ProjectView project(String projectKey) {
        return projects().stream().filter(project -> project.projectKey().equals(projectKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + projectKey));
    }

    private long projectId(String projectKey) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM xhs_monitor_projects WHERE project_key = ?",
                (rs, row) -> rs.getLong("id"), projectKey);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("项目不存在：" + projectKey);
        }
        return ids.get(0);
    }

    private void replaceTerms(long projectId, List<String> terms, Instant now) {
        jdbcTemplate.update("DELETE FROM xhs_monitor_terms WHERE project_id = ?", projectId);
        normalizeTerms(terms).forEach(term -> jdbcTemplate.update("""
                INSERT INTO xhs_monitor_terms(project_id, term_type, term_value, enabled, created_at)
                VALUES (?, 'KEYWORD', ?, 1, ?)
                """, projectId, term, Timestamp.from(now)));
    }

    private List<String> normalizeTerms(List<String> terms) {
        if (terms == null) {
            return List.of();
        }
        return terms.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isBlank())
                .distinct().limit(50).toList();
    }

    private ProjectView mapProject(ResultSet rs, int row) throws SQLException {
        String terms = rs.getString("terms");
        return new ProjectView(rs.getLong("id"), rs.getString("project_key"), rs.getString("name"),
                rs.getString("status"), terms == null || terms.isBlank() ? List.of() : List.of(terms.split("\\n")),
                rs.getInt("post_count"), rs.getInt("active_incident_count"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private JobView mapJob(ResultSet rs, int row) throws SQLException {
        return new JobView(rs.getString("job_key"), rs.getString("project_key"), rs.getString("project_name"),
                rs.getString("query_text"), rs.getString("status"), rs.getBoolean("complete"),
                rs.getInt("record_count"), rs.getInt("attempt_count"), rs.getString("error_code"),
                rs.getString("error_message"), instant(rs, "started_at"), instant(rs, "finished_at"));
    }

    private OpinionRow mapOpinion(ResultSet rs, int row) throws SQLException {
        return new OpinionRow(rs.getLong("id"), rs.getString("project_key"), rs.getString("title"),
                anonymousAuthor(rs.getString("author_key")), rs.getString("summary"), rs.getString("sentiment"),
                rs.getString("risk_category"), rs.getInt("risk_score"), riskLevel(rs.getInt("risk_score")),
                rs.getLong("liked_count"), rs.getLong("collected_count"), rs.getLong("comment_count"),
                rs.getLong("share_count"), safeSourceUrl(rs.getString("source_url")), instant(rs, "published_at"),
                instant(rs, "analyzed_at"));
    }

    private PostDetail mapPost(ResultSet rs, int row) throws SQLException {
        return new PostDetail(rs.getLong("id"), rs.getString("project_key"), rs.getString("title"),
                rs.getString("content"), anonymousAuthor(rs.getString("author_key")), safeSourceUrl(rs.getString("source_url")),
                rs.getString("note_type"), json(rs.getString("tags_json")), rs.getString("sentiment"),
                nullableDouble(rs, "sentiment_score"), rs.getString("risk_category"), nullableInteger(rs, "risk_score"),
                nullableDouble(rs, "confidence"), rs.getString("summary"), json(rs.getString("evidence_json")),
                json(rs.getString("explanation_json")), rs.getLong("liked_count"), rs.getLong("collected_count"),
                rs.getLong("comment_count"), rs.getLong("share_count"), instant(rs, "published_at"),
                instant(rs, "last_collected_at"), instant(rs, "analyzed_at"), List.of());
    }

    private JsonNode json(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createArrayNode() : objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.createArrayNode();
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private String anonymousAuthor(String authorKey) {
        if (authorKey == null || authorKey.isBlank()) {
            return "匿名作者";
        }
        String suffix = authorKey.length() <= 6 ? authorKey : authorKey.substring(authorKey.length() - 6);
        return "匿名作者 " + suffix;
    }

    private String safeSourceUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value.strip());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return "";
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("xiaohongshu.com") || normalizedHost.endsWith(".xiaohongshu.com")
                    ? uri.toASCIIString()
                    : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String projectKey(String value) {
        String key = required(value, "项目标识");
        if (!PROJECT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("项目标识只能包含英文字母、数字、下划线和短横线");
        }
        return key;
    }

    private String normalizeProjectStatus(String value) {
        String status = required(value, "项目状态").toUpperCase(Locale.ROOT);
        if (!PROJECT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("项目状态只能是 ACTIVE 或 PAUSED");
        }
        return status;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.strip();
    }

    private int safeLimit(int value, int fallback) {
        return Math.max(1, Math.min(value <= 0 ? fallback : value, 100));
    }

    private String riskLevel(int score) {
        return score >= 80 ? "CRITICAL" : score >= 60 ? "WARNING" : score >= 40 ? "WATCH" : "NORMAL";
    }

    public record ProjectView(long id, String projectKey, String name, String status, List<String> terms,
                              int postCount, int activeIncidentCount, Instant createdAt, Instant updatedAt) {
    }

    public record OverviewView(int postCount, int analyzedCount, int negativeCount, int highRiskCount,
                               int activeIncidentCount, int failedJobCount) {
    }

    public record JobView(String jobKey, String projectKey, String projectName, String query, String status,
                          boolean complete, int recordCount, int attemptCount, String errorCode,
                          String errorMessage, Instant startedAt, Instant finishedAt) {
    }

    public record OpinionRow(long postId, String projectKey, String title, String authorDisplayName, String summary,
                             String sentiment, String riskCategory, int riskScore, String riskLevel,
                             long likedCount, long collectedCount, long commentCount, long shareCount,
                             String sourceUrl, Instant publishedAt, Instant analyzedAt) {
    }

    public record CommentView(long commentId, String authorDisplayName, String content, long likedCount,
                              Instant publishedAt) {
    }

    public record PostDetail(long postId, String projectKey, String title, String content,
                             String authorDisplayName, String sourceUrl, String noteType, JsonNode tags,
                             String sentiment, Double sentimentScore, String riskCategory, Integer riskScore,
                             Double confidence, String summary, JsonNode evidence, JsonNode explanation,
                             long likedCount, long collectedCount, long commentCount, long shareCount,
                             Instant publishedAt, Instant collectedAt, Instant analyzedAt,
                             List<CommentView> comments) {
        PostDetail withComments(List<CommentView> value) {
            return new PostDetail(postId, projectKey, title, content, authorDisplayName, sourceUrl, noteType, tags,
                    sentiment, sentimentScore, riskCategory, riskScore, confidence, summary, evidence, explanation,
                    likedCount, collectedCount, commentCount, shareCount, publishedAt, collectedAt, analyzedAt, value);
        }
    }

    public record IncidentTransitionView(long incidentId, String projectKey, String fromStatus,
                                         String toStatus, boolean changed) {
    }

    public record AlertRuleView(long id, String projectKey, String name, int minimumRiskScore,
                                int cooldownMinutes, boolean enabled, Instant createdAt, Instant updatedAt) {
    }

    public record AlertEventView(long id, String projectKey, String ruleName, long incidentId, String title,
                                 String status, int riskScore, Instant sentAt, Instant acknowledgedAt,
                                 Instant createdAt) {
    }

    private record IncidentState(XhsIncidentStatus status, String projectKey) {
    }
}
