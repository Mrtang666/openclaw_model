package com.example.spring.xhs.console;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.incident.XhsIncidentStatus;
import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.link.XhsImageUrlPolicy;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.report.XhsReportArtifactStorage;
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
    private static final List<String> GENERIC_COLLECTION_TERMS = List.of(
            "避雷", "踩雷", "排雷", "差评", "吐槽", "不推荐", "劝退", "黑榜", "翻车",
            "帖子", "笔记", "测评", "小红书");
    private static final String EFFECTIVE_RISK = """
            GREATEST(a.risk_score, COALESCE(ca.highest_comment_risk_score, 0),
                     COALESCE(ia.highest_image_risk_score, 0))
            """.strip();
    private static final String EFFECTIVE_SENTIMENT = """
            CASE WHEN COALESCE(ca.negative_comment_count, 0) > 0
                       OR COALESCE(ia.negative_image_count, 0) > 0
                 THEN 'NEGATIVE' ELSE a.sentiment END
            """.strip();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final XhsCollectionCoordinator collectionCoordinator;
    private final XhsDailyReportService dailyReportService;
    private final XhsReportArtifactStorage reportArtifactStorage;

    public XhsConsoleService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            XhsCollectionCoordinator collectionCoordinator,
            XhsDailyReportService dailyReportService,
            XhsReportArtifactStorage reportArtifactStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.collectionCoordinator = collectionCoordinator;
        this.dailyReportService = dailyReportService;
        this.reportArtifactStorage = reportArtifactStorage;
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

    public AnalysisMetrics analysisMetrics(String projectKey, int days) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        Instant since = Instant.now().minusSeconds(Math.max(1, Math.min(days, 90)) * 86400L);
        AnalysisMetrics body = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) calls,
                       COALESCE(SUM(e.prompt_tokens), 0) prompt_tokens,
                       COALESCE(SUM(e.completion_tokens), 0) completion_tokens,
                       COALESCE(SUM(e.total_tokens), 0) total_tokens,
                       COALESCE(ROUND(AVG(e.duration_ms)), 0) average_duration_ms,
                       COALESCE(SUM(CASE WHEN e.status = 'FALLBACK' THEN 1 ELSE 0 END), 0) fallback_calls,
                       COALESCE(SUM(CASE WHEN e.status = 'CACHE' THEN 1 ELSE 0 END), 0) cache_calls
                FROM xhs_analysis_executions e
                JOIN xhs_posts p ON p.id = e.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                WHERE e.created_at >= ? AND (? IS NULL OR pr.project_key = ?)
                """, (rs, row) -> new AnalysisMetrics(
                rs.getLong("calls"), rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"), rs.getLong("average_duration_ms"), rs.getLong("fallback_calls"),
                rs.getLong("cache_calls")),
                Timestamp.from(since), filter, filter);
        CommentModelMetrics comments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) calls,
                       COALESCE(SUM(comment_count), 0) comment_count,
                       COALESCE(SUM(prompt_tokens), 0) prompt_tokens,
                       COALESCE(SUM(completion_tokens), 0) completion_tokens,
                       COALESCE(SUM(total_tokens), 0) total_tokens,
                       COALESCE(ROUND(AVG(duration_ms)), 0) average_duration_ms,
                       COALESCE(SUM(status = 'FAILED'), 0) failed_calls
                FROM xhs_comment_analysis_executions
                WHERE created_at >= ?
                """, (rs, row) -> new CommentModelMetrics(
                rs.getLong("calls"), rs.getLong("comment_count"), rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"), rs.getLong("total_tokens"),
                rs.getLong("average_duration_ms"), rs.getLong("failed_calls")), Timestamp.from(since));
        AnalysisMetrics safeBody = body == null ? AnalysisMetrics.empty() : body;
        CommentModelMetrics safeComments = comments == null ? CommentModelMetrics.empty() : comments;
        ImageModelMetrics images = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) calls,
                       COALESCE(SUM(e.prompt_tokens), 0) prompt_tokens,
                       COALESCE(SUM(e.completion_tokens), 0) completion_tokens,
                       COALESCE(SUM(e.total_tokens), 0) total_tokens,
                       COALESCE(ROUND(AVG(e.duration_ms)), 0) average_duration_ms,
                       COALESCE(SUM(e.status = 'FAILED'), 0) failed_calls,
                       COALESCE(SUM(e.status = 'CACHE'), 0) cache_calls
                FROM xhs_image_analysis_executions e
                JOIN xhs_posts p ON p.id = e.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                WHERE e.created_at >= ? AND (? IS NULL OR pr.project_key = ?)
                """, (rs, row) -> new ImageModelMetrics(
                rs.getLong("calls"), rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"), rs.getLong("average_duration_ms"),
                rs.getLong("failed_calls"), rs.getLong("cache_calls")),
                Timestamp.from(since), filter, filter);
        return safeBody.withModels(safeComments, images == null ? ImageModelMetrics.empty() : images);
    }

    public CoverageMetrics coverageMetrics(String projectKey, int days) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        Instant since = Instant.now().minusSeconds(Math.max(1, Math.min(days, 90)) * 86400L);
        SearchCoverage search = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) executions,
                       COALESCE(SUM(se.status = 'SUCCEEDED'), 0) succeeded,
                       COALESCE(SUM(se.status = 'PARTIAL'), 0) partial,
                       COALESCE(SUM(se.status = 'FAILED'), 0) failed,
                       (SELECT COUNT(DISTINCT h.post_id)
                        FROM xhs_post_search_hits h
                        JOIN xhs_search_executions he ON he.id = h.search_execution_id
                        JOIN xhs_monitor_projects hp ON hp.id = he.project_id
                        WHERE he.started_at >= ? AND (? IS NULL OR hp.project_key = ?)) unique_posts,
                       COALESCE(SUM(se.raw_count), 0) raw_count,
                       COALESCE(SUM(se.imported_count), 0) imported_count,
                       COUNT(DISTINCT CONCAT(se.sort_mode, ':', se.time_range, ':', se.note_type)) strategies
                FROM xhs_search_executions se
                JOIN xhs_monitor_projects p ON p.id = se.project_id
                WHERE se.started_at >= ? AND (? IS NULL OR p.project_key = ?)
                """, (rs, row) -> new SearchCoverage(
                rs.getInt("executions"), rs.getInt("succeeded"), rs.getInt("partial"),
                rs.getInt("failed"), rs.getInt("unique_posts"), rs.getLong("raw_count"),
                rs.getLong("imported_count"), rs.getInt("strategies")),
                Timestamp.from(since), filter, filter, Timestamp.from(since), filter, filter);
        CollectionCoverage collection = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) tracked_posts,
                       COALESCE(SUM(c.comments_status = 'FULL'), 0) comments_full,
                       COALESCE(SUM(c.comments_status = 'PARTIAL'), 0) comments_partial,
                       COALESCE(SUM(c.comments_status = 'FAILED'), 0) comments_failed,
                       COALESCE(SUM(c.collected_comment_count), 0) collected_comments,
                       COALESCE(SUM(c.expected_comment_count), 0) expected_comments,
                       COALESCE(SUM(c.discovered_image_count), 0) discovered_images
                FROM xhs_post_collection_completeness c
                JOIN xhs_posts po ON po.id = c.post_id
                JOIN xhs_monitor_projects p ON p.id = po.project_id
                WHERE (? IS NULL OR p.project_key = ?)
                """, (rs, row) -> new CollectionCoverage(
                rs.getInt("tracked_posts"), rs.getInt("comments_full"), rs.getInt("comments_partial"),
                rs.getInt("comments_failed"), rs.getLong("collected_comments"),
                rs.getLong("expected_comments"), rs.getLong("discovered_images")), filter, filter);
        AssetCoverage assets = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT i.id) image_count,
                       COUNT(DISTINCT CASE WHEN i.analysis_status = 'SUCCEEDED' THEN i.id END) images_analyzed,
                       COUNT(DISTINCT CASE WHEN i.analysis_status = 'PENDING' THEN i.id END) images_pending,
                       COUNT(DISTINCT CASE WHEN i.analysis_status = 'FAILED' THEN i.id END) images_failed,
                       COUNT(DISTINCT CASE WHEN i.sentiment = 'NEGATIVE' THEN i.id END) negative_images,
                       COUNT(DISTINCT ca.id) comments_analyzed,
                       COUNT(DISTINCT CASE WHEN ca.analysis_method = 'LLM'
                           AND ca.analysis_status = 'SUCCEEDED' THEN ca.id END) comments_reviewed,
                       COUNT(DISTINCT CASE WHEN ca.analysis_status = 'PENDING' THEN ca.id END) comments_pending,
                       COUNT(DISTINCT CASE WHEN ca.is_negative THEN ca.id END) negative_comments
                FROM xhs_monitor_projects p
                LEFT JOIN xhs_posts po ON po.project_id = p.id
                LEFT JOIN xhs_post_images i ON i.post_id = po.id
                LEFT JOIN xhs_comment_analysis_results ca ON ca.post_id = po.id
                WHERE (? IS NULL OR p.project_key = ?)
                """, (rs, row) -> new AssetCoverage(
                rs.getInt("image_count"), rs.getInt("images_analyzed"), rs.getInt("images_pending"),
                rs.getInt("images_failed"), rs.getInt("negative_images"),
                rs.getInt("comments_analyzed"), rs.getInt("comments_reviewed"),
                rs.getInt("comments_pending"), rs.getInt("negative_comments")), filter, filter);
        return CoverageMetrics.of(search, collection, assets, Math.max(1, Math.min(days, 90)));
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
        List<String> reportStorageKeys = jdbcTemplate.query("""
                SELECT a.storage_key FROM xhs_report_artifacts a
                JOIN xhs_report_runs r ON r.id = a.run_id
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                WHERE s.project_id = ?
                """, (rs, row) -> rs.getString("storage_key"), projectId);
        reportStorageKeys.forEach(reportArtifactStorage::delete);
        // Remove post/project delivery records before deleting schedules and posts.
        jdbcTemplate.update("DELETE FROM xhs_negative_post_deliveries WHERE project_id = ?", projectId);
        jdbcTemplate.update("""
                DELETE d FROM xhs_report_deliveries d
                JOIN xhs_report_runs r ON r.id = d.run_id
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                WHERE s.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE a FROM xhs_report_artifacts a
                JOIN xhs_report_runs r ON r.id = a.run_id
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                WHERE s.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE r FROM xhs_report_runs r
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                WHERE s.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE rr FROM xhs_report_recipients rr
                JOIN xhs_report_schedules s ON s.id = rr.schedule_id
                WHERE s.project_id = ?
                """, projectId);
        jdbcTemplate.update("DELETE FROM xhs_report_schedules WHERE project_id = ?", projectId);
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
                DELETE e FROM xhs_analysis_executions e
                JOIN xhs_posts p ON p.id = e.post_id
                WHERE p.project_id = ?
                """, projectId);
        jdbcTemplate.update("""
                DELETE f FROM xhs_analysis_feedback f
                JOIN xhs_posts p ON p.id = f.post_id
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
                    COUNT(DISTINCT CASE WHEN a.post_id IS NOT NULL OR ca.post_id IS NOT NULL OR ia.post_id IS NOT NULL
                        THEN p.id END) analyzed_count,
                    COUNT(DISTINCT CASE WHEN a.sentiment = 'NEGATIVE'
                        OR COALESCE(ca.negative_count, 0) > 0 OR COALESCE(ia.negative_count, 0) > 0
                        THEN p.id END) negative_count,
                    COUNT(DISTINCT CASE WHEN GREATEST(COALESCE(a.risk_score, 0),
                        COALESCE(ca.maximum_risk_score, 0), COALESCE(ia.maximum_risk_score, 0)) >= 60
                        THEN p.id END) high_risk_count,
                    COUNT(DISTINCT CASE WHEN i.status <> 'RESOLVED' THEN i.id END) active_incident_count,
                    (SELECT COUNT(*) FROM xhs_collection_jobs j
                     JOIN xhs_monitor_projects jp ON jp.id = j.project_id
                     WHERE j.status = 'FAILED' AND (? IS NULL OR jp.project_key = ?)) failed_job_count
                FROM xhs_monitor_projects pr
                LEFT JOIN xhs_posts p ON p.project_id = pr.id
                LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                LEFT JOIN (
                    SELECT post_id, SUM(is_negative) negative_count,
                           MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) maximum_risk_score
                    FROM xhs_comment_analysis_results GROUP BY post_id
                ) ca ON ca.post_id = p.id
                LEFT JOIN (
                    SELECT post_id, SUM(sentiment = 'NEGATIVE') negative_count,
                           MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) maximum_risk_score
                    FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                ) ia ON ia.post_id = p.id
                LEFT JOIN xhs_incidents i ON i.project_id = pr.id
                WHERE (? IS NULL OR pr.project_key = ?)
                """, (rs, row) -> new OverviewView(
                        rs.getInt("post_count"), rs.getInt("analyzed_count"), rs.getInt("negative_count"),
                        rs.getInt("high_risk_count"), rs.getInt("active_incident_count"),
                        rs.getInt("failed_job_count")), filter, filter, filter, filter);
    }

    public String startCollection(String projectKey, String query, int limit) {
        return startCollection(projectKey, query, limit, "GENERAL", "ANY", "ALL");
    }

    public String startCollection(String projectKey, String query, int limit,
                                  String sortMode, String timeRange, String noteType) {
        return startCollection(projectKey, query, limit, sortMode, timeRange, noteType, 100);
    }

    public String startCollection(String projectKey, String query, int limit,
                                  String sortMode, String timeRange, String noteType,
                                  int commentLimit) {
        ProjectView project = project(projectKey(projectKey));
        if (!"ACTIVE".equals(project.status())) {
            throw new IllegalStateException("项目已暂停，不能启动采集");
        }
        String actualQuery = normalizeCollectionQuery(project, query);
        if (actualQuery.isBlank()) {
            throw new IllegalArgumentException("请先配置项目关键词或输入本次采集关键词");
        }
        return collectionCoordinator.start(new XhsCollectionRequest(
                project.projectKey(), project.name(), actualQuery, limit, "",
                sortMode, timeRange, noteType, commentLimit));
    }

    public CollectionPlanResult startCoverageCollection(String projectKey, int limit) {
        ProjectView project = project(projectKey(projectKey));
        if (!"ACTIVE".equals(project.status())) {
            throw new IllegalStateException("项目已暂停，不能启动采集");
        }
        List<String> terms = project.terms().stream()
                .filter(term -> !isGenericCollectionQuery(term))
                .limit(5)
                .toList();
        if (terms.isEmpty()) {
            terms = List.of(project.name());
        }
        List<String> jobKeys = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String term : terms) {
            List<CollectionStrategy> strategies = List.of(
                    new CollectionStrategy(term, "GENERAL", "ANY", "ALL", 100),
                    new CollectionStrategy(term, "LATEST", "DAY", "ALL", 100),
                    new CollectionStrategy(appendRiskKeyword(term), "COMMENTS", "HALF_YEAR", "ALL", 300));
            for (CollectionStrategy strategy : strategies) {
                try {
                    jobKeys.add(startCollection(project.projectKey(), strategy.query(), limit,
                            strategy.sortMode(), strategy.timeRange(), strategy.noteType(),
                            strategy.commentLimit()));
                } catch (RuntimeException exception) {
                    errors.add(strategy.query() + ": " + exceptionMessage(exception));
                }
            }
        }
        if (jobKeys.isEmpty() && !errors.isEmpty()) {
            throw new IllegalStateException(errors.get(0));
        }
        return new CollectionPlanResult(jobKeys, errors, terms.size(), jobKeys.size());
    }

    public List<JobView> jobs(String projectKey, int limit) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey(projectKey);
        return jdbcTemplate.query("""
                SELECT j.job_key, p.project_key, p.name project_name, j.query_text, j.status,
                       j.complete, j.record_count, j.attempt_count, j.error_code, j.error_message,
                       j.started_at, j.finished_at,
                       COALESCE(se.completeness_status, 'NOT_STARTED') completeness_status,
                       COALESCE(se.raw_count, 0) raw_count,
                       COALESCE(se.imported_count, j.record_count, 0) imported_count,
                       COALESCE(se.comment_count, 0) comment_count,
                       COALESCE(se.skipped_count, 0) skipped_count,
                       COALESCE(se.sort_mode, 'GENERAL') sort_mode,
                       COALESCE(se.time_range, 'ANY') time_range,
                       COALESCE(se.note_type, 'ALL') strategy_note_type,
                       COALESCE(se.requested_comment_limit, 100) requested_comment_limit
                FROM xhs_collection_jobs j
                JOIN xhs_monitor_projects p ON p.id = j.project_id
                LEFT JOIN xhs_search_executions se ON se.job_key = j.job_key
                WHERE (? IS NULL OR p.project_key = ?)
                ORDER BY j.started_at DESC, j.id DESC
                LIMIT ?
                """, this::mapJob, filter, filter, safeLimit(limit, 50));
    }

    public OpinionPage opinions(
            String projectKey, String keyword, String sentiment, boolean commentNegativeOnly,
            boolean consultationNegativeOnly, boolean imageNegativeOnly,
            Instant publishedFrom, Instant publishedTo,
            String sortBy, String sortDirection, int minimumRiskScore, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE " + EFFECTIVE_RISK + " >= ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(Math.max(0, Math.min(minimumRiskScore, 100)));
        if (projectKey != null && !projectKey.isBlank()) {
            where.append(" AND pr.project_key = ?");
            arguments.add(projectKey(projectKey));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.title LIKE ? OR p.content LIKE ? OR a.summary LIKE ?)");
            String like = "%" + keyword.strip() + "%";
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            where.append(" AND ").append(EFFECTIVE_SENTIMENT).append(" = ?");
            arguments.add(sentiment.strip().toUpperCase(Locale.ROOT));
        }
        if (commentNegativeOnly) {
            where.append(" AND COALESCE(ca.negative_comment_count, 0) > 0");
        }
        if (consultationNegativeOnly) {
            where.append("""
                     AND COALESCE(ca.negative_comment_count, 0) > 0
                     AND (p.note_type LIKE '%\u54a8\u8be2%' OR p.title LIKE '%\u5417%'
                          OR p.title LIKE '%\u600e\u4e48%' OR p.title LIKE '%\u6c42\u63a8\u8350%'
                          OR p.title LIKE '%\u8bf7\u95ee%')
                    """);
        }
        if (imageNegativeOnly) {
            where.append(" AND COALESCE(ia.negative_image_count, 0) > 0");
        }
        if (publishedFrom != null) {
            where.append(" AND p.published_at >= ?");
            arguments.add(Timestamp.from(publishedFrom));
        }
        if (publishedTo != null) {
            where.append(" AND p.published_at <= ?");
            arguments.add(Timestamp.from(publishedTo));
        }
        String joins = """
                FROM xhs_posts p
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                JOIN xhs_analysis_results a ON a.id = (
                    SELECT a2.id FROM xhs_analysis_results a2
                    WHERE a2.post_id = p.id ORDER BY a2.analyzed_at DESC, a2.id DESC LIMIT 1)
                LEFT JOIN (
                    SELECT post_id,
                           SUM(CASE WHEN is_negative THEN 1 ELSE 0 END) negative_comment_count,
                           MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) highest_comment_risk_score
                    FROM xhs_comment_analysis_results GROUP BY post_id
                ) ca ON ca.post_id = p.id
                LEFT JOIN (
                    SELECT post_id,
                           SUM(CASE WHEN sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) negative_image_count,
                           MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) highest_image_risk_score
                    FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                ) ia ON ia.post_id = p.id
                """;
        Integer totalValue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + joins + where, Integer.class, arguments.toArray());
        int safePageSize = 20;
        int safePage = Math.max(1, page);
        int total = totalValue == null ? 0 : totalValue;
        int totalPages = Math.max(1, (total + safePageSize - 1) / safePageSize);
        safePage = Math.min(safePage, totalPages);
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, pr.project_key, p.title, p.author_key, p.source_url, p.published_at,
                       a.summary,
                """).append(EFFECTIVE_SENTIMENT).append("""
                 AS sentiment, a.risk_category,
                """).append(EFFECTIVE_RISK).append("""
                 AS risk_score, a.analyzed_at,
                       COALESCE(m.liked_count, 0) liked_count,
                       COALESCE(m.collected_count, 0) collected_count,
                       COALESCE(m.comment_count, 0) comment_count,
                       COALESCE(m.share_count, 0) share_count,
                       COALESCE(ca.negative_comment_count, 0) negative_comment_count,
                       COALESCE(ca.highest_comment_risk_score, 0) highest_comment_risk_score,
                       COALESCE(ia.negative_image_count, 0) negative_image_count,
                       COALESCE(ia.highest_image_risk_score, 0) highest_image_risk_score,
                       CASE WHEN p.note_type LIKE '%\u54a8\u8be2%' OR p.title LIKE '%\u5417%'
                                 OR p.title LIKE '%\u600e\u4e48%' OR p.title LIKE '%\u6c42\u63a8\u8350%'
                                 OR p.title LIKE '%\u8bf7\u95ee%' THEN TRUE ELSE FALSE END consultation
                """).append(joins).append("""
                LEFT JOIN xhs_metric_snapshots m ON m.id = (
                    SELECT m2.id FROM xhs_metric_snapshots m2
                    WHERE m2.post_id = p.id ORDER BY m2.snapshot_at DESC, m2.id DESC LIMIT 1)
                """).append(where);
        sql.append(" ORDER BY CASE WHEN p.published_at IS NULL THEN 1 ELSE 0 END, ");
        sql.append(opinionSort(sortBy));
        sql.append(" ").append("ASC".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC");
        sql.append(", p.id DESC LIMIT ? OFFSET ?");
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(safePageSize);
        pageArguments.add((safePage - 1) * safePageSize);
        List<OpinionRow> items = jdbcTemplate.query(sql.toString(), this::mapOpinion, pageArguments.toArray());
        return new OpinionPage(items, safePage, safePageSize, total, totalPages);
    }

    private String opinionSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.strip()) {
            case "riskScore" -> EFFECTIVE_RISK;
            case "analyzedAt" -> "a.analyzed_at";
            case "likedCount" -> "COALESCE(m.liked_count, 0)";
            case "commentCount" -> "COALESCE(m.comment_count, 0)";
            case "publishedAt", "" -> "p.published_at";
            default -> "p.published_at";
        };
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
                SELECT c.id, c.author_key, c.content, c.liked_count, c.published_at,
                       COALESCE(a.sentiment, 'NEUTRAL') comment_sentiment,
                       COALESCE(a.risk_score, 0) comment_risk_score,
                       COALESCE(a.is_negative, FALSE) comment_negative,
                       COALESCE(a.analysis_method, 'RULE') comment_analysis_method,
                       COALESCE(a.confidence, 0) comment_confidence,
                       COALESCE(a.summary, '') comment_summary,
                       COALESCE(a.analysis_status, 'PENDING') comment_analysis_status
                FROM xhs_comments c
                LEFT JOIN xhs_comment_analysis_results a
                    ON a.post_id = c.post_id AND a.source_comment_id = c.source_comment_id
                WHERE c.post_id = ?
                ORDER BY a.is_negative DESC, a.risk_score DESC, c.liked_count DESC,
                         c.published_at DESC LIMIT 50
                """, (rs, row) -> new CommentView(rs.getLong("id"), anonymousAuthor(rs.getString("author_key")),
                        rs.getString("content"), rs.getLong("liked_count"), instant(rs, "published_at"),
                        rs.getString("comment_sentiment"), rs.getInt("comment_risk_score"),
                        rs.getBoolean("comment_negative"), rs.getString("comment_analysis_method"),
                        rs.getDouble("comment_confidence"), rs.getString("comment_summary"),
                        rs.getString("comment_analysis_status")), postId);
        List<ImageView> images = jdbcTemplate.query("""
                SELECT id, image_url, image_order, analysis_status, sentiment,
                       risk_score, summary, evidence_json
                FROM xhs_post_images WHERE post_id = ?
                ORDER BY image_order, id LIMIT 20
                """, (rs, row) -> new ImageView(rs.getLong("id"),
                        XhsImageUrlPolicy.sanitize(rs.getString("image_url")),
                        rs.getInt("image_order"), rs.getString("analysis_status"),
                        rs.getString("sentiment"), rs.getInt("risk_score"),
                        rs.getString("summary"), json(rs.getString("evidence_json"))), postId)
                .stream().filter(image -> !image.imageUrl().isBlank()).toList();
        return post.withAssets(comments, images);
    }

    @Transactional
    public AnalysisFeedback submitAnalysisFeedback(long postId, String feedbackType, String note) {
        String type = feedbackType == null ? "" : feedbackType.strip().toUpperCase(Locale.ROOT);
        if (!List.of("CORRECT", "SENTIMENT_WRONG", "RISK_TOO_HIGH", "RISK_TOO_LOW").contains(type)) {
            throw new IllegalArgumentException("不支持的分析反馈类型");
        }
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xhs_posts WHERE id = ?", Integer.class, postId);
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("帖子不存在");
        }
        String safeNote = note == null ? "" : note.strip();
        if (safeNote.length() > 1000) {
            throw new IllegalArgumentException("反馈说明不能超过 1000 字");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO xhs_analysis_feedback(post_id, feedback_type, note, status, created_at)
                VALUES (?, ?, ?, 'OPEN', ?)
                """, postId, type, safeNote.isBlank() ? null : safeNote, Timestamp.from(now));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new AnalysisFeedback(id == null ? 0 : id, postId, type, safeNote, "OPEN", now);
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

    static String normalizeCollectionQuery(ProjectView project, String query) {
        String candidate = query == null || query.isBlank()
                ? String.join(" ", project.terms()).strip()
                : query.strip();
        if (candidate.isBlank()) {
            return "";
        }
        if (!isGenericCollectionQuery(candidate)) {
            return candidate;
        }
        String productTerm = project.terms().stream()
                .filter(term -> !isGenericCollectionQuery(term))
                .findFirst()
                .orElse(project.name());
        return productTerm.strip() + " " + candidate;
    }

    static boolean isGenericCollectionQuery(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String meaningfulText = query.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
        for (String term : GENERIC_COLLECTION_TERMS) {
            meaningfulText = meaningfulText.replace(term, "");
        }
        return meaningfulText.isBlank();
    }

    private static String appendRiskKeyword(String term) {
        String normalized = term.strip();
        return normalized.contains("避雷") ? normalized : normalized + " 避雷";
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
                rs.getString("error_message"), instant(rs, "started_at"), instant(rs, "finished_at"),
                rs.getString("completeness_status"), rs.getInt("raw_count"), rs.getInt("imported_count"),
                rs.getInt("comment_count"), rs.getInt("skipped_count"),
                rs.getString("sort_mode"), rs.getString("time_range"),
                rs.getString("strategy_note_type"), rs.getInt("requested_comment_limit"));
    }

    private OpinionRow mapOpinion(ResultSet rs, int row) throws SQLException {
        return new OpinionRow(rs.getLong("id"), rs.getString("project_key"), rs.getString("title"),
                anonymousAuthor(rs.getString("author_key")), rs.getString("summary"), rs.getString("sentiment"),
                rs.getString("risk_category"), rs.getInt("risk_score"), riskLevel(rs.getInt("risk_score")),
                rs.getLong("liked_count"), rs.getLong("collected_count"), rs.getLong("comment_count"),
                rs.getLong("share_count"), rs.getBoolean("consultation"),
                rs.getInt("negative_comment_count"), rs.getInt("highest_comment_risk_score"),
                rs.getInt("negative_image_count"), rs.getInt("highest_image_risk_score"),
                safeSourceUrl(rs.getString("source_url")), instant(rs, "published_at"),
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
                instant(rs, "last_collected_at"), instant(rs, "analyzed_at"), List.of(), List.of());
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

    private String exceptionMessage(RuntimeException exception) {
        String value = exception == null || exception.getMessage() == null
                ? "collection submission failed" : exception.getMessage().strip();
        return value.length() <= 300 ? value : value.substring(0, 300);
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

    public record AnalysisMetrics(long calls, long promptTokens, long completionTokens,
                                  long totalTokens, long averageDurationMs, long fallbackCalls, long cacheCalls,
                                  long commentCalls, long reviewedComments, long commentPromptTokens,
                                  long commentCompletionTokens, long commentTotalTokens,
                                  long commentAverageDurationMs, long commentFailedCalls,
                                  long imageCalls, long imagePromptTokens, long imageCompletionTokens,
                                  long imageTotalTokens, long imageAverageDurationMs,
                                  long imageFailedCalls, long imageCacheCalls) {
        public AnalysisMetrics(long calls, long promptTokens, long completionTokens,
                               long totalTokens, long averageDurationMs, long fallbackCalls, long cacheCalls) {
            this(calls, promptTokens, completionTokens, totalTokens, averageDurationMs, fallbackCalls, cacheCalls,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private static AnalysisMetrics empty() {
            return new AnalysisMetrics(0, 0, 0, 0, 0, 0, 0);
        }

        private AnalysisMetrics withModels(CommentModelMetrics value, ImageModelMetrics images) {
            return new AnalysisMetrics(calls, promptTokens, completionTokens, totalTokens,
                    averageDurationMs, fallbackCalls, cacheCalls, value.calls(), value.commentCount(),
                    value.promptTokens(), value.completionTokens(), value.totalTokens(),
                    value.averageDurationMs(), value.failedCalls(), images.calls(), images.promptTokens(),
                    images.completionTokens(), images.totalTokens(), images.averageDurationMs(),
                    images.failedCalls(), images.cacheCalls());
        }
    }

    public record CoverageMetrics(int days, int searchExecutions, int successfulSearches,
                                  int partialSearches, int failedSearches, int searchStrategies,
                                  int uniquePostsFound, long rawPosts, long importedPosts, int importRate,
                                  int trackedPosts, int commentsFull, int commentsPartial, int commentsFailed,
                                  long collectedComments, long expectedComments, int commentCoverageRate,
                                  long discoveredImages, int imageCount, int imagesAnalyzed, int imagesPending,
                                  int imagesFailed, int negativeImages, int imageAnalysisRate,
                                  int commentsAnalyzed, int commentsReviewed, int commentsPending,
                                  int negativeComments) {
        private static CoverageMetrics of(
                SearchCoverage search, CollectionCoverage collection, AssetCoverage assets, int days) {
            SearchCoverage s = search == null ? SearchCoverage.empty() : search;
            CollectionCoverage c = collection == null ? CollectionCoverage.empty() : collection;
            AssetCoverage a = assets == null ? AssetCoverage.empty() : assets;
            return new CoverageMetrics(days, s.executions(), s.succeeded(), s.partial(), s.failed(),
                    s.strategies(), s.uniquePosts(), s.rawCount(), s.importedCount(),
                    percent(s.importedCount(), s.rawCount()), c.trackedPosts(), c.commentsFull(),
                    c.commentsPartial(), c.commentsFailed(), c.collectedComments(), c.expectedComments(),
                    percent(c.collectedComments(), c.expectedComments()), c.discoveredImages(),
                    a.imageCount(), a.imagesAnalyzed(), a.imagesPending(), a.imagesFailed(), a.negativeImages(),
                    percent(a.imagesAnalyzed(), a.imageCount()), a.commentsAnalyzed(), a.commentsReviewed(),
                    a.commentsPending(), a.negativeComments());
        }

        private static int percent(long value, long total) {
            return total <= 0 ? 0 : (int) Math.min(100, Math.round(value * 100.0 / total));
        }
    }

    private record CommentModelMetrics(long calls, long commentCount, long promptTokens,
                                       long completionTokens, long totalTokens,
                                       long averageDurationMs, long failedCalls) {
        private static CommentModelMetrics empty() { return new CommentModelMetrics(0, 0, 0, 0, 0, 0, 0); }
    }

    private record ImageModelMetrics(long calls, long promptTokens, long completionTokens,
                                     long totalTokens, long averageDurationMs,
                                     long failedCalls, long cacheCalls) {
        private static ImageModelMetrics empty() { return new ImageModelMetrics(0, 0, 0, 0, 0, 0, 0); }
    }

    private record SearchCoverage(int executions, int succeeded, int partial, int failed,
                                  int uniquePosts, long rawCount, long importedCount, int strategies) {
        private static SearchCoverage empty() { return new SearchCoverage(0, 0, 0, 0, 0, 0, 0, 0); }
    }

    private record CollectionCoverage(int trackedPosts, int commentsFull, int commentsPartial,
                                      int commentsFailed, long collectedComments,
                                      long expectedComments, long discoveredImages) {
        private static CollectionCoverage empty() { return new CollectionCoverage(0, 0, 0, 0, 0, 0, 0); }
    }

    private record AssetCoverage(int imageCount, int imagesAnalyzed, int imagesPending, int imagesFailed,
                                 int negativeImages, int commentsAnalyzed, int commentsReviewed,
                                 int commentsPending, int negativeComments) {
        private static AssetCoverage empty() { return new AssetCoverage(0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }

    public record JobView(String jobKey, String projectKey, String projectName, String query, String status,
                          boolean complete, int recordCount, int attemptCount, String errorCode,
                          String errorMessage, Instant startedAt, Instant finishedAt,
                          String completenessStatus, int rawCount, int importedCount, int commentCount,
                          int skippedCount, String sortMode, String timeRange, String noteType,
                          int requestedCommentLimit) {
    }

    public record OpinionRow(long postId, String projectKey, String title, String authorDisplayName, String summary,
                             String sentiment, String riskCategory, int riskScore, String riskLevel,
                             long likedCount, long collectedCount, long commentCount, long shareCount,
                             boolean consultation, int negativeCommentCount, int highestCommentRiskScore,
                             int negativeImageCount, int highestImageRiskScore,
                             String sourceUrl, Instant publishedAt, Instant analyzedAt) {
    }

    public record OpinionPage(List<OpinionRow> items, int page, int pageSize, int total, int totalPages) {
    }

    public record CollectionPlanResult(List<String> jobKeys, List<String> errors,
                                       int keywordCount, int submittedCount) {
    }

    private record CollectionStrategy(String query, String sortMode, String timeRange,
                                      String noteType, int commentLimit) {
    }

    public record CommentView(long commentId, String authorDisplayName, String content, long likedCount,
                              Instant publishedAt, String sentiment, int riskScore, boolean negative,
                              String analysisMethod, double confidence, String analysisSummary,
                              String analysisStatus) {
    }

    public record ImageView(long imageId, String imageUrl, int imageOrder, String analysisStatus,
                            String sentiment, int riskScore, String summary, JsonNode evidence) {
    }

    public record AnalysisFeedback(long id, long postId, String feedbackType, String note,
                                   String status, Instant createdAt) {
    }

    public record PostDetail(long postId, String projectKey, String title, String content,
                             String authorDisplayName, String sourceUrl, String noteType, JsonNode tags,
                             String sentiment, Double sentimentScore, String riskCategory, Integer riskScore,
                             Double confidence, String summary, JsonNode evidence, JsonNode explanation,
                             long likedCount, long collectedCount, long commentCount, long shareCount,
                             Instant publishedAt, Instant collectedAt, Instant analyzedAt,
                             List<CommentView> comments, List<ImageView> images) {
        PostDetail withAssets(List<CommentView> commentValues, List<ImageView> imageValues) {
            return new PostDetail(postId, projectKey, title, content, authorDisplayName, sourceUrl, noteType, tags,
                    sentiment, sentimentScore, riskCategory, riskScore, confidence, summary, evidence, explanation,
                    likedCount, collectedCount, commentCount, shareCount, publishedAt, collectedAt, analyzedAt,
                    commentValues, imageValues);
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
