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
                               COALESCE(SUM(body_sentiment = 'NEGATIVE'
                                   OR negative_comment_count > 0 OR negative_image_count > 0), 0) negative_posts,
                               COALESCE(SUM(negative_comment_count > 0), 0) negative_comment_posts,
                               COALESCE(SUM(negative_image_count > 0), 0) negative_image_posts,
                               COALESCE(SUM(effective_risk_score >= 60), 0) high_risk_posts,
                               COALESCE(ROUND(AVG(effective_risk_score)), 0) average_risk_score
                        FROM (
                            SELECT p.id, a.sentiment body_sentiment,
                                   COALESCE(ca.negative_comment_count, 0) negative_comment_count,
                                   COALESCE(ia.negative_image_count, 0) negative_image_count,
                                   GREATEST(COALESCE(a.risk_score, 0),
                                       COALESCE(ca.highest_comment_risk_score, 0),
                                       COALESCE(ia.highest_image_risk_score, 0)) effective_risk_score
                            FROM xhs_posts p
                            LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                            LEFT JOIN (
                                SELECT post_id, SUM(is_negative) negative_comment_count,
                                       MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) highest_comment_risk_score,
                                       MAX(analyzed_at) last_analyzed_at
                                FROM xhs_comment_analysis_results GROUP BY post_id
                            ) ca ON ca.post_id = p.id
                            LEFT JOIN (
                                SELECT post_id, SUM(sentiment = 'NEGATIVE') negative_image_count,
                                       MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) highest_image_risk_score,
                                       MAX(analyzed_at) last_analyzed_at
                                FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                            ) ia ON ia.post_id = p.id
                            WHERE p.project_id = ? AND (
                                (a.analyzed_at >= ? AND a.analyzed_at < ?)
                                OR (ca.last_analyzed_at >= ? AND ca.last_analyzed_at < ?)
                                OR (ia.last_analyzed_at >= ? AND ia.last_analyzed_at < ?))
                        ) effective_posts
                        """, (rs, row) -> new AnalysisStats(
                        rs.getInt("analyzed_posts"), rs.getInt("negative_posts"),
                        rs.getInt("negative_comment_posts"), rs.getInt("negative_image_posts"),
                        rs.getInt("high_risk_posts"), rs.getInt("average_risk_score")),
                projectId, start, end, start, end, start, end);
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
                        SELECT effective_category risk_category, COUNT(*) post_count,
                               ROUND(AVG(effective_risk_score)) average_risk_score,
                               MAX(effective_risk_score) maximum_risk_score
                        FROM (
                            SELECT CASE
                                       WHEN COALESCE(ca.highest_comment_risk_score, 0) > COALESCE(a.risk_score, 0)
                                            AND COALESCE(ca.highest_comment_risk_score, 0)
                                                >= COALESCE(ia.highest_image_risk_score, 0)
                                           THEN '评论负面反馈'
                                       WHEN COALESCE(ia.highest_image_risk_score, 0) > COALESCE(a.risk_score, 0)
                                            AND COALESCE(ia.highest_image_risk_score, 0)
                                                > COALESCE(ca.highest_comment_risk_score, 0)
                                           THEN '图片负面反馈'
                                       ELSE COALESCE(NULLIF(a.risk_category, ''), '其他')
                                   END effective_category,
                                   GREATEST(COALESCE(a.risk_score, 0),
                                       COALESCE(ca.highest_comment_risk_score, 0),
                                       COALESCE(ia.highest_image_risk_score, 0)) effective_risk_score
                            FROM xhs_posts p
                            LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                            LEFT JOIN (
                                SELECT post_id,
                                       MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) highest_comment_risk_score,
                                       MAX(analyzed_at) last_analyzed_at
                                FROM xhs_comment_analysis_results GROUP BY post_id
                            ) ca ON ca.post_id = p.id
                            LEFT JOIN (
                                SELECT post_id,
                                       MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) highest_image_risk_score,
                                       MAX(analyzed_at) last_analyzed_at
                                FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                            ) ia ON ia.post_id = p.id
                            WHERE p.project_id = ? AND (
                                (a.analyzed_at >= ? AND a.analyzed_at < ?)
                                OR (ca.last_analyzed_at >= ? AND ca.last_analyzed_at < ?)
                                OR (ia.last_analyzed_at >= ? AND ia.last_analyzed_at < ?))
                        ) effective_posts
                        WHERE effective_risk_score > 0
                        GROUP BY effective_category
                        ORDER BY maximum_risk_score DESC, post_count DESC
                        LIMIT 5
                        """, (rs, row) -> new XhsRiskCategorySummary(
                        rs.getString("risk_category"), rs.getInt("post_count"),
                        rs.getInt("average_risk_score"), rs.getInt("maximum_risk_score")),
                projectId, start, end, start, end, start, end);
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
                        SELECT p.id, p.title, a.summary, a.sentiment body_sentiment, a.risk_category,
                               COALESCE(a.risk_score, 0) body_risk_score,
                               COALESCE(ca.negative_comment_count, 0) negative_comment_count,
                               COALESCE(ca.highest_comment_risk_score, 0) highest_comment_risk_score,
                               COALESCE(ia.negative_image_count, 0) negative_image_count,
                               COALESCE(ia.highest_image_risk_score, 0) highest_image_risk_score,
                               GREATEST(COALESCE(a.risk_score, 0),
                                   COALESCE(ca.highest_comment_risk_score, 0),
                                   COALESCE(ia.highest_image_risk_score, 0)) effective_risk_score,
                               p.published_at
                        FROM xhs_posts p
                        LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                        LEFT JOIN (
                            SELECT post_id, SUM(is_negative) negative_comment_count,
                                   MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) highest_comment_risk_score,
                                   MAX(analyzed_at) last_analyzed_at
                            FROM xhs_comment_analysis_results GROUP BY post_id
                        ) ca ON ca.post_id = p.id
                        LEFT JOIN (
                            SELECT post_id, SUM(sentiment = 'NEGATIVE') negative_image_count,
                                   MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) highest_image_risk_score,
                                   MAX(analyzed_at) last_analyzed_at
                            FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                        ) ia ON ia.post_id = p.id
                        WHERE p.project_id = ? AND (
                            (a.analyzed_at >= ? AND a.analyzed_at < ?)
                            OR (ca.last_analyzed_at >= ? AND ca.last_analyzed_at < ?)
                            OR (ia.last_analyzed_at >= ? AND ia.last_analyzed_at < ?))
                        ORDER BY effective_risk_score DESC,
                                 GREATEST(COALESCE(a.analyzed_at, '1970-01-01'),
                                          COALESCE(ca.last_analyzed_at, '1970-01-01'),
                                          COALESCE(ia.last_analyzed_at, '1970-01-01')) DESC,
                                 p.id DESC
                        LIMIT 10
                        """, this::mapReportPost,
                projectId, start, end, start, end, start, end);
        AnalysisStats safeAnalysis = analysis == null ? AnalysisStats.empty() : analysis;
        return new XhsDailyReport(
                projectKey, project.name(), reportDate, periodStart, periodEnd, collectedPosts,
                safeAnalysis.analyzedPosts(), safeAnalysis.negativePosts(),
                safeAnalysis.negativeCommentPosts(), safeAnalysis.negativeImagePosts(),
                safeAnalysis.highRiskPosts(),
                newIncidents, activeIncidents, resolvedIncidents, safeAnalysis.averageRiskScore(),
                categories, topIncidents, topRiskPosts);
    }

    private XhsReportPostSummary mapReportPost(ResultSet rs, int row) throws SQLException {
        int bodyRisk = rs.getInt("body_risk_score");
        int commentCount = rs.getInt("negative_comment_count");
        int commentRisk = rs.getInt("highest_comment_risk_score");
        int imageCount = rs.getInt("negative_image_count");
        int imageRisk = rs.getInt("highest_image_risk_score");
        int effectiveRisk = rs.getInt("effective_risk_score");
        String sentiment = "NEGATIVE".equals(rs.getString("body_sentiment"))
                || commentCount > 0 || imageCount > 0 ? "NEGATIVE" : "NEUTRAL";
        String category = rs.getString("risk_category");
        if (commentRisk > bodyRisk && commentRisk >= imageRisk) {
            category = "评论负面反馈";
        } else if (imageRisk > bodyRisk && imageRisk > commentRisk) {
            category = "图片负面反馈";
        }
        return new XhsReportPostSummary(
                rs.getLong("id"), rs.getString("title"), rs.getString("summary"), sentiment,
                category == null || category.isBlank() ? "其他" : category, effectiveRisk,
                riskSource(bodyRisk, commentRisk, imageRisk, effectiveRisk), bodyRisk,
                commentCount, commentRisk, imageCount, imageRisk, instant(rs, "published_at"));
    }

    private String riskSource(int bodyRisk, int commentRisk, int imageRisk, int effectiveRisk) {
        java.util.ArrayList<String> sources = new java.util.ArrayList<>(3);
        if (bodyRisk == effectiveRisk && bodyRisk > 0) sources.add("正文");
        if (commentRisk == effectiveRisk && commentRisk > 0) sources.add("评论");
        if (imageRisk == effectiveRisk && imageRisk > 0) sources.add("图片");
        return sources.isEmpty() ? "无明显风险" : String.join(" / ", sources);
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
            int negativeCommentPosts,
            int negativeImagePosts,
            int highRiskPosts,
            int averageRiskScore) {

        private static AnalysisStats empty() {
            return new AnalysisStats(0, 0, 0, 0, 0, 0);
        }
    }

    private record ProjectRef(long id, String name) {
    }
}
