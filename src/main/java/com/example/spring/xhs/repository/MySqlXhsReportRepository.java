package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsRiskCategorySummary;
import com.example.spring.xhs.report.XhsReportPostSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class MySqlXhsReportRepository implements XhsReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlXhsReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public XhsDailyReport loadDailyReport(
            String projectKey,
            LocalDate reportDate,
            Instant periodStart,
            Instant periodEnd,
            int topIncidentLimit) {
        List<ProjectRef> projects = jdbcTemplate.query(
                "SELECT id, name FROM xhs_monitor_projects WHERE project_key = ? AND status = 'ACTIVE'",
                (rs, row) -> new ProjectRef(rs.getLong("id"), rs.getString("name")), projectKey);
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("未找到启用的小红书舆情项目：" + projectKey);
        }
        ProjectRef project = projects.get(0);
        long projectId = project.id();
        Timestamp start = Timestamp.from(periodStart);
        Timestamp end = Timestamp.from(periodEnd);
        int collectedPosts = count("""
                SELECT COUNT(*) FROM xhs_posts
                WHERE project_id = ? AND last_collected_at >= ? AND last_collected_at < ?
                """, projectId, start, end);
        AnalysisStats analysis = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) analyzed_posts,
                               COALESCE(SUM(a.sentiment = 'NEGATIVE'), 0) negative_posts,
                               COALESCE(SUM(a.risk_score >= 60), 0) high_risk_posts,
                               COALESCE(ROUND(AVG(a.risk_score)), 0) average_risk_score
                        FROM xhs_analysis_results a
                        JOIN xhs_posts p ON p.id = a.post_id
                        WHERE p.project_id = ? AND a.analyzed_at >= ? AND a.analyzed_at < ?
                        """, (rs, row) -> new AnalysisStats(
                        rs.getInt("analyzed_posts"), rs.getInt("negative_posts"),
                        rs.getInt("high_risk_posts"), rs.getInt("average_risk_score")),
                projectId, start, end);
        int newIncidents = count("""
                SELECT COUNT(*) FROM xhs_incidents
                WHERE project_id = ? AND created_at >= ? AND created_at < ?
                """, projectId, start, end);
        int activeIncidents = count("""
                SELECT COUNT(*) FROM xhs_incidents
                WHERE project_id = ? AND status <> 'RESOLVED'
                """, projectId);
        int resolvedIncidents = count("""
                SELECT COUNT(DISTINCT a.incident_id)
                FROM xhs_incident_actions a
                JOIN xhs_incidents i ON i.id = a.incident_id
                WHERE i.project_id = ? AND a.to_status = 'RESOLVED'
                  AND a.created_at >= ? AND a.created_at < ?
                """, projectId, start, end);
        List<XhsRiskCategorySummary> categories = jdbcTemplate.query("""
                        SELECT a.risk_category, COUNT(*) post_count,
                               ROUND(AVG(a.risk_score)) average_risk_score,
                               MAX(a.risk_score) maximum_risk_score
                        FROM xhs_analysis_results a
                        JOIN xhs_posts p ON p.id = a.post_id
                        WHERE p.project_id = ? AND a.analyzed_at >= ? AND a.analyzed_at < ?
                        GROUP BY a.risk_category
                        ORDER BY maximum_risk_score DESC, post_count DESC
                        LIMIT 5
                        """, (rs, row) -> new XhsRiskCategorySummary(
                        rs.getString("risk_category"), rs.getInt("post_count"),
                        rs.getInt("average_risk_score"), rs.getInt("maximum_risk_score")),
                projectId, start, end);
        List<XhsIncidentView> topIncidents = jdbcTemplate.query("""
                        SELECT i.id, p.project_key, i.title, i.risk_category, i.status,
                               i.risk_score, i.risk_level, i.post_count,
                               i.first_seen_at, i.last_seen_at
                        FROM xhs_incidents i
                        JOIN xhs_monitor_projects p ON p.id = i.project_id
                        WHERE i.project_id = ? AND i.status <> 'RESOLVED'
                        ORDER BY i.risk_score DESC, i.last_seen_at DESC
                        LIMIT ?
                        """, this::mapIncident, projectId, topIncidentLimit);
        List<XhsReportPostSummary> topRiskPosts = jdbcTemplate.query("""
                        SELECT p.id, p.title, a.summary, a.sentiment, a.risk_category,
                               a.risk_score, p.published_at
                        FROM xhs_analysis_results a
                        JOIN xhs_posts p ON p.id = a.post_id
                        WHERE p.project_id = ? AND a.analyzed_at >= ? AND a.analyzed_at < ?
                        ORDER BY a.risk_score DESC, a.analyzed_at DESC, p.id DESC
                        LIMIT 10
                        """, (rs, row) -> new XhsReportPostSummary(
                        rs.getLong("id"), rs.getString("title"), rs.getString("summary"),
                        rs.getString("sentiment"), rs.getString("risk_category"),
                        rs.getInt("risk_score"), instant(rs, "published_at")),
                projectId, start, end);
        AnalysisStats safeAnalysis = analysis == null ? AnalysisStats.empty() : analysis;
        return new XhsDailyReport(
                projectKey, project.name(), reportDate, periodStart, periodEnd, collectedPosts,
                safeAnalysis.analyzedPosts(), safeAnalysis.negativePosts(), safeAnalysis.highRiskPosts(),
                newIncidents, activeIncidents, resolvedIncidents, safeAnalysis.averageRiskScore(),
                categories, topIncidents, topRiskPosts);
    }

    private int count(String sql, Object... arguments) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return result == null ? 0 : result;
    }

    private XhsIncidentView mapIncident(ResultSet rs, int row) throws SQLException {
        return new XhsIncidentView(
                rs.getLong("id"), rs.getString("project_key"), rs.getString("title"),
                rs.getString("risk_category"), rs.getString("status"), rs.getInt("risk_score"),
                rs.getString("risk_level"), rs.getInt("post_count"),
                instant(rs, "first_seen_at"), instant(rs, "last_seen_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record AnalysisStats(
            int analyzedPosts,
            int negativePosts,
            int highRiskPosts,
            int averageRiskScore) {

        private static AnalysisStats empty() {
            return new AnalysisStats(0, 0, 0, 0);
        }
    }

    private record ProjectRef(long id, String name) {
    }
}
