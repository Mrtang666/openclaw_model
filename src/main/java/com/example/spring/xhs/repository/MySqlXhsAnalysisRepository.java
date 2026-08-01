package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsAnalysisResult;
import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.analysis.XhsOpinionView;
import com.example.spring.xhs.analysis.XhsSentiment;
import com.example.spring.xhs.analysis.XhsTrendSignals;
import com.example.spring.xhs.model.XhsMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MySqlXhsAnalysisRepository implements XhsAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlXhsAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<XhsAnalysisCandidate> findUnanalyzed(String analysisVersion, int limit) {
        return jdbcTemplate.query("""
                        SELECT p.id, p.project_id, pr.project_key, p.title, p.content, p.source_url,
                               p.published_at, p.last_collected_at,
                               COALESCE(m.liked_count, 0) liked_count,
                               COALESCE(m.collected_count, 0) collected_count,
                               COALESCE(m.comment_count, 0) comment_count,
                               COALESCE(m.share_count, 0) share_count
                        FROM xhs_posts p
                        JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                        LEFT JOIN xhs_metric_snapshots m ON m.id = (
                            SELECT m2.id FROM xhs_metric_snapshots m2
                            WHERE m2.post_id = p.id ORDER BY m2.snapshot_at DESC, m2.id DESC LIMIT 1)
                        WHERE NOT EXISTS (
                            SELECT 1 FROM xhs_analysis_results a
                            WHERE a.post_id = p.id AND a.analysis_version = ?)
                        ORDER BY p.last_collected_at, p.id
                        LIMIT ?
                        """,
                this::mapCandidate,
                analysisVersion,
                Math.max(1, limit));
    }

    @Override
    public void saveAnalysis(XhsAnalysisResult result) {
        jdbcTemplate.update("""
                        INSERT INTO xhs_analysis_results(
                            post_id, analysis_version, sentiment, sentiment_score, aspects_json,
                            risk_category, severity, risk_score, confidence, summary,
                            evidence_json, explanation_json, review_status, analyzed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            sentiment = VALUES(sentiment), sentiment_score = VALUES(sentiment_score),
                            aspects_json = VALUES(aspects_json), risk_category = VALUES(risk_category),
                            severity = VALUES(severity), risk_score = VALUES(risk_score),
                            confidence = VALUES(confidence), summary = VALUES(summary),
                            evidence_json = VALUES(evidence_json), explanation_json = VALUES(explanation_json),
                            review_status = VALUES(review_status), analyzed_at = VALUES(analyzed_at)
                        """,
                result.postId(), result.analysisVersion(), result.semantic().sentiment().name(),
                result.semantic().sentimentScore(), json(result.semantic().aspects()),
                result.semantic().riskCategory(), result.semantic().severity(), result.risk().riskScore(),
                result.semantic().confidence(), result.semantic().summary(), json(result.semantic().evidence()),
                json(result.risk().components()), result.reviewStatus(), Timestamp.from(result.analyzedAt()));
    }

    @Override
    public XhsTrendSignals loadTrendSignals(long postId, long projectId, String riskCategory, Instant since) {
        List<MetricPoint> points = jdbcTemplate.query("""
                        SELECT snapshot_at, liked_count, collected_count, comment_count, share_count
                        FROM xhs_metric_snapshots
                        WHERE post_id = ?
                        ORDER BY snapshot_at DESC, id DESC
                        LIMIT 2
                        """,
                (rs, row) -> new MetricPoint(
                        rs.getTimestamp("snapshot_at").toInstant(),
                        weightedEngagement(
                                rs.getLong("liked_count"), rs.getLong("collected_count"),
                                rs.getLong("comment_count"), rs.getLong("share_count"))),
                postId);
        double growthPerHour = 0;
        if (points.size() == 2) {
            MetricPoint latest = points.get(0);
            MetricPoint previous = points.get(1);
            double hours = Math.max(1.0 / 60.0,
                    java.time.Duration.between(previous.snapshotAt(), latest.snapshotAt()).toMillis() / 3_600_000.0);
            growthPerHour = Math.max(0, latest.engagement() - previous.engagement()) / hours;
        }
        Integer recurrence = jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT p.id)
                        FROM xhs_analysis_results a
                        JOIN xhs_posts p ON p.id = a.post_id
                        WHERE p.project_id = ? AND a.risk_category = ? AND a.analyzed_at >= ?
                        """,
                Integer.class, projectId, riskCategory, Timestamp.from(since));
        return new XhsTrendSignals(growthPerHour, recurrence == null ? 0 : recurrence);
    }

    @Override
    @Transactional
    public long upsertIncident(long projectId, String incidentKey, String category, String title,
                               int riskScore, String riskLevel, Instant observedAt) {
        Instant observed = observedAt == null ? Instant.now() : observedAt;
        Instant now = Instant.now();
        List<String> previousStatuses = jdbcTemplate.query("""
                        SELECT status FROM xhs_incidents
                        WHERE project_id = ? AND incident_key = ?
                        FOR UPDATE
                        """, (rs, row) -> rs.getString("status"), projectId, incidentKey);
        jdbcTemplate.update("""
                        INSERT INTO xhs_incidents(
                            project_id, incident_key, risk_category, title, status, risk_level,
                            risk_score, post_count, first_seen_at, last_seen_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'OPEN', ?, ?, 0, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            title = VALUES(title), risk_level = VALUES(risk_level),
                            risk_score = GREATEST(risk_score, VALUES(risk_score)),
                            last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at)),
                            status = IF(status = 'RESOLVED', 'INVESTIGATING', status),
                            updated_at = VALUES(updated_at)
                        """,
                projectId, incidentKey, category, title, riskLevel, riskScore,
                Timestamp.from(observed), Timestamp.from(observed), Timestamp.from(now), Timestamp.from(now));
        long incidentId = jdbcTemplate.queryForObject(
                "SELECT id FROM xhs_incidents WHERE project_id = ? AND incident_key = ?",
                Long.class, projectId, incidentKey);
        if (!previousStatuses.isEmpty() && "RESOLVED".equals(previousStatuses.get(0))) {
            jdbcTemplate.update("""
                            INSERT INTO xhs_incident_actions(
                                incident_id, from_status, to_status, actor_connection_id,
                                actor_recipient_id, note, created_at)
                            VALUES (?, 'RESOLVED', 'INVESTIGATING', 'SYSTEM', 'SYSTEM', ?, ?)
                            """, incidentId, "检测到新的高风险关联笔记，自动重新打开事件", Timestamp.from(now));
        }
        return incidentId;
    }

    @Override
    @Transactional
    public void linkIncidentPost(long incidentId, long postId, Instant linkedAt) {
        int inserted = jdbcTemplate.update("""
                        INSERT IGNORE INTO xhs_incident_posts(incident_id, post_id, linked_at)
                        VALUES (?, ?, ?)
                        """, incidentId, postId, Timestamp.from(linkedAt));
        if (inserted > 0) {
            jdbcTemplate.update("""
                    UPDATE xhs_incidents SET post_count = post_count + 1
                    WHERE id = ?
                    """, incidentId);
        }
    }

    @Override
    public List<XhsOpinionView> searchOpinions(String projectKey, String keyword, String sentiment,
                                               int minimumRiskScore, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT pr.project_key, p.title, a.summary, a.sentiment, a.risk_category,
                       a.risk_score, p.source_url, p.published_at, a.analyzed_at
                FROM xhs_analysis_results a
                JOIN xhs_posts p ON p.id = a.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                WHERE pr.project_key = ? AND a.risk_score >= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(projectKey);
        args.add(Math.max(0, minimumRiskScore));
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.title LIKE ? OR p.content LIKE ? OR a.summary LIKE ?)");
            String like = "%" + keyword.strip() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            sql.append(" AND a.sentiment = ?");
            args.add(XhsSentiment.from(sentiment).name());
        }
        sql.append(" ORDER BY a.risk_score DESC, a.analyzed_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 50)));
        return jdbcTemplate.query(sql.toString(), this::mapOpinion, args.toArray());
    }

    @Override
    public List<XhsIncidentView> listIncidents(String projectKey, String status, int limit) {
        String safeStatus = status == null || status.isBlank() ? "OPEN" : status.strip().toUpperCase(java.util.Locale.ROOT);
        return jdbcTemplate.query("""
                        SELECT i.id, p.project_key, i.title, i.risk_category, i.status, i.risk_score,
                               i.risk_level, i.post_count, i.first_seen_at, i.last_seen_at
                        FROM xhs_incidents i
                        JOIN xhs_monitor_projects p ON p.id = i.project_id
                        WHERE p.project_key = ? AND i.status = ?
                        ORDER BY i.risk_score DESC, i.last_seen_at DESC
                        LIMIT ?
                        """, this::mapIncident, projectKey, safeStatus, Math.max(1, Math.min(limit, 50)));
    }

    private XhsAnalysisCandidate mapCandidate(ResultSet rs, int row) throws SQLException {
        return new XhsAnalysisCandidate(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("project_key"),
                rs.getString("title"), rs.getString("content"), rs.getString("source_url"),
                instant(rs, "published_at"), instant(rs, "last_collected_at"),
                new XhsMetrics(rs.getLong("liked_count"), rs.getLong("collected_count"),
                        rs.getLong("comment_count"), rs.getLong("share_count")));
    }

    private XhsOpinionView mapOpinion(ResultSet rs, int row) throws SQLException {
        int riskScore = rs.getInt("risk_score");
        return new XhsOpinionView(
                rs.getString("project_key"), rs.getString("title"), rs.getString("summary"),
                XhsSentiment.from(rs.getString("sentiment")), rs.getString("risk_category"),
                riskScore, riskLevel(riskScore), rs.getString("source_url"),
                instant(rs, "published_at"), instant(rs, "analyzed_at"));
    }

    private XhsIncidentView mapIncident(ResultSet rs, int row) throws SQLException {
        return new XhsIncidentView(
                rs.getLong("id"), rs.getString("project_key"), rs.getString("title"), rs.getString("risk_category"),
                rs.getString("status"), rs.getInt("risk_score"), rs.getString("risk_level"),
                rs.getInt("post_count"), instant(rs, "first_seen_at"), instant(rs, "last_seen_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化舆情分析结果", exception);
        }
    }

    private String riskLevel(int score) {
        return score >= 80 ? "CRITICAL" : score >= 60 ? "WARNING" : score >= 40 ? "WATCH" : "NORMAL";
    }

    private long weightedEngagement(long likes, long collects, long comments, long shares) {
        return Math.max(0, likes) + Math.max(0, collects) * 2
                + Math.max(0, comments) * 3 + Math.max(0, shares) * 4;
    }

    private record MetricPoint(Instant snapshotAt, long engagement) {
    }
}
