package com.example.spring.xhs.schedule;

import com.example.spring.xhs.config.XhsScheduledReportProperties;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportDocxService;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.report.XhsDailyReportXlsxService;
import com.example.spring.xhs.report.XhsReportArtifactStorage;
import com.example.spring.xhs.source.XhsCollectionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class XhsScheduledReportExecutionService {

    private final JdbcTemplate jdbcTemplate;
    private final XhsCollectionCoordinator collectionCoordinator;
    private final XhsDailyReportService reportService;
    private final XhsDailyReportDocxService docxService;
    private final XhsDailyReportXlsxService xlsxService;
    private final XhsReportArtifactStorage storage;
    private final XhsConsoleUrlService consoleUrlService;
    private final XhsScheduledReportProperties properties;

    public XhsScheduledReportExecutionService(
            JdbcTemplate jdbcTemplate,
            XhsCollectionCoordinator collectionCoordinator,
            XhsDailyReportService reportService,
            XhsDailyReportDocxService docxService,
            XhsDailyReportXlsxService xlsxService,
            XhsReportArtifactStorage storage,
            XhsConsoleUrlService consoleUrlService,
            XhsScheduledReportProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.collectionCoordinator = collectionCoordinator;
        this.reportService = reportService;
        this.docxService = docxService;
        this.xlsxService = xlsxService;
        this.storage = storage;
        this.consoleUrlService = consoleUrlService;
        this.properties = properties;
    }

    public int processPending() {
        List<RunRow> runs = jdbcTemplate.query("""
                SELECT r.*, s.project_id, s.frequency, s.formats, s.collect_before_report,
                       s.collection_limit, s.top_post_limit, s.timezone,
                       p.project_key, p.name project_name
                FROM xhs_report_runs r
                JOIN xhs_report_schedules s ON s.id = r.schedule_id
                JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE r.status IN ('QUEUED', 'COLLECTING', 'ANALYZING')
                ORDER BY r.created_at, r.id LIMIT 10
                """, this::mapRun);
        runs.forEach(this::process);
        return runs.size();
    }

    private void process(RunRow run) {
        try {
            switch (run.status()) {
                case "QUEUED" -> start(run);
                case "COLLECTING" -> pollCollection(run);
                case "ANALYZING" -> pollAnalysis(run);
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            fail(run.id(), exception);
        }
    }

    private void start(RunRow run) {
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE xhs_report_runs SET started_at = ?, stage_started_at = ?, updated_at = ? WHERE id = ? AND status = 'QUEUED'",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), run.id());
        if (!run.collectBeforeReport()) {
            advance(run.id(), "ANALYZING", now.plus(properties.analysisWait()), "");
            return;
        }
        List<String> terms = jdbcTemplate.query("""
                SELECT term_value FROM xhs_monitor_terms
                WHERE project_id = ? AND enabled = 1 ORDER BY id
                """, (rs, row) -> rs.getString("term_value"), run.projectId());
        if (terms.isEmpty()) {
            advance(run.id(), "ANALYZING", now.plus(properties.analysisWait()), "项目没有启用的采集关键词");
            return;
        }
        StringBuilder errors = new StringBuilder();
        for (String term : terms) {
            try {
                String jobKey = collectionCoordinator.start(new XhsCollectionRequest(
                        run.projectKey(), run.projectName(), term, run.collectionLimit(), ""));
                jdbcTemplate.update("""
                        INSERT INTO xhs_report_run_collection_jobs(run_id, job_key, created_at)
                        VALUES (?, ?, ?)
                        """, run.id(), jobKey, Timestamp.from(now));
            } catch (RuntimeException exception) {
                if (!errors.isEmpty()) {
                    errors.append("；");
                }
                errors.append(term).append("：").append(safeMessage(exception));
            }
        }
        advance(run.id(), "COLLECTING", now.plus(properties.collectionWait()), errors.toString());
    }

    private void pollCollection(RunRow run) {
        int pending = count("""
                SELECT COUNT(*) FROM xhs_collection_jobs j
                JOIN xhs_report_run_collection_jobs rc ON rc.job_key = j.job_key
                WHERE rc.run_id = ? AND j.status IN ('PENDING', 'SUBMITTED', 'RUNNING')
                """, run.id());
        Instant now = Instant.now();
        if (pending > 0 && run.deadlineAt() != null && now.isBefore(run.deadlineAt())) {
            return;
        }
        List<CollectionOutcome> incomplete = jdbcTemplate.query("""
                SELECT j.query_text, j.status, j.record_count, j.error_code, j.error_message
                FROM xhs_collection_jobs j
                JOIN xhs_report_run_collection_jobs rc ON rc.job_key = j.job_key
                WHERE rc.run_id = ? AND j.status IN ('FAILED', 'PARTIAL')
                ORDER BY j.id
                """, (rs, row) -> new CollectionOutcome(
                rs.getString("query_text"), rs.getString("status"), rs.getInt("record_count"),
                text(rs, "error_code"), text(rs, "error_message")), run.id());
        String reason = run.partialReason();
        if (pending > 0) {
            reason = append(reason, "采集等待超时，仍有 " + pending + " 个任务未完成");
        }
        if (!incomplete.isEmpty()) {
            reason = append(reason, collectionReason(incomplete));
        }
        advance(run.id(), "ANALYZING", now.plus(properties.analysisWait()), reason);
    }

    private void pollAnalysis(RunRow run) {
        int pending = count("""
                SELECT COUNT(*) FROM xhs_posts p
                WHERE p.project_id = ? AND p.last_collected_at >= ?
                  AND NOT EXISTS (SELECT 1 FROM xhs_analysis_results a WHERE a.post_id = p.id)
                """, run.projectId(), Timestamp.from(run.startedAt()));
        Instant now = Instant.now();
        if (pending > 0 && run.deadlineAt() != null && now.isBefore(run.deadlineAt())) {
            return;
        }
        String reason = pending > 0
                ? append(run.partialReason(), "分析等待超时，仍有 " + pending + " 篇笔记未分析")
                : run.partialReason();
        generate(run, reason, now);
    }

    private void generate(RunRow run, String partialReason, Instant periodEnd) {
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE xhs_report_runs SET status = 'GENERATING', period_end = ?, partial_reason = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(periodEnd), emptyToNull(partialReason), Timestamp.from(now), run.id());
        ZoneId zone = ZoneId.of(run.timezone());
        Instant periodStart = XhsReportScheduleCalculator.periodStart(run.frequency(), periodEnd, zone);
        XhsDailyReport report = reportService.reportPeriod(
                run.projectKey(), periodEnd.atZone(zone).toLocalDate(), periodStart, periodEnd, run.topPostLimit());
        for (String format : run.formats()) {
            if ("DOCX".equals(format)) {
                XhsDailyReportDocxService.ReportDocument document = docxService.generate(report, consoleUrlService);
                store(run, format, document.fileName(), document.contentType(), document.bytes(), now);
            } else if ("XLSX".equals(format)) {
                XhsDailyReportXlsxService.ReportWorkbook workbook = xlsxService.generate(report, consoleUrlService);
                store(run, format, workbook.fileName(), workbook.contentType(), workbook.bytes(), now);
            }
        }
        int recipients = jdbcTemplate.update("""
                INSERT IGNORE INTO xhs_report_deliveries(
                    run_id, recipient_id, status, attempt_count, next_attempt_at, created_at, updated_at)
                SELECT ?, id, 'PENDING', 0, ?, ?, ? FROM xhs_report_recipients
                WHERE schedule_id = ? AND enabled = 1
                """, run.id(), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), run.scheduleId());
        String finalStatus = recipients > 0 ? "DELIVERING" : partialReason.isBlank() ? "SUCCEEDED" : "PARTIAL";
        jdbcTemplate.update("""
                UPDATE xhs_report_runs SET status = ?, period_start = ?, period_end = ?,
                    finished_at = CASE WHEN ? = 'DELIVERING' THEN NULL ELSE ? END, updated_at = ? WHERE id = ?
                """, finalStatus, Timestamp.from(periodStart), Timestamp.from(periodEnd), finalStatus,
                Timestamp.from(now), Timestamp.from(now), run.id());
    }

    private void store(RunRow run, String format, String fileName, String contentType, byte[] bytes, Instant now) {
        XhsReportArtifactStorage.StoredArtifact artifact = storage.store(
                run.id(), run.projectKey(), format, fileName, contentType, bytes);
        jdbcTemplate.update("""
                INSERT INTO xhs_report_artifacts(
                    run_id, format, file_name, storage_key, content_type, size_bytes, sha256, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE file_name = VALUES(file_name), storage_key = VALUES(storage_key),
                    content_type = VALUES(content_type), size_bytes = VALUES(size_bytes), sha256 = VALUES(sha256),
                    expires_at = VALUES(expires_at), created_at = VALUES(created_at)
                """, run.id(), format, artifact.fileName(), artifact.storageKey(), artifact.contentType(),
                artifact.sizeBytes(), artifact.sha256(), Timestamp.from(now.plusSeconds(properties.retentionDays() * 86400L)),
                Timestamp.from(now));
    }

    private void advance(long runId, String status, Instant deadline, String partialReason) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_report_runs SET status = ?, stage_started_at = ?, deadline_at = ?,
                    partial_reason = ?, updated_at = ? WHERE id = ?
                """, status, Timestamp.from(now), Timestamp.from(deadline), emptyToNull(partialReason),
                Timestamp.from(now), runId);
    }

    private void fail(long runId, Throwable throwable) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_report_runs SET status = 'FAILED', error_message = ?, finished_at = ?, updated_at = ? WHERE id = ?
                """, safeMessage(throwable), Timestamp.from(now), Timestamp.from(now), runId);
    }

    private RunRow mapRun(ResultSet rs, int row) throws SQLException {
        return new RunRow(
                rs.getLong("id"), rs.getLong("schedule_id"), rs.getLong("project_id"),
                rs.getString("project_key"), rs.getString("project_name"), rs.getString("frequency"),
                Arrays.stream(rs.getString("formats").split(",")).map(String::strip).toList(),
                rs.getBoolean("collect_before_report"), rs.getInt("collection_limit"),
                rs.getInt("top_post_limit"), rs.getString("timezone"), rs.getString("status"),
                instant(rs, "deadline_at"), text(rs, "partial_reason"), instant(rs, "started_at"));
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private String append(String current, String value) {
        return current == null || current.isBlank() ? value : current + "；" + value;
    }

    private String collectionReason(List<CollectionOutcome> outcomes) {
        return outcomes.stream().map(value -> {
            String result = value.query() + "：" + ("FAILED".equals(value.status()) ? "采集失败" :
                    "已采集 " + value.recordCount() + " 条，部分笔记未获取");
            String detail = !value.errorMessage().isBlank() ? value.errorMessage() : value.errorCode();
            return detail.isBlank() ? result : result + "（" + detail + "）";
        }).collect(java.util.stream.Collectors.joining("；"));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        String value = message == null || message.isBlank()
                ? (throwable == null ? "未知错误" : throwable.getClass().getSimpleName()) : message;
        return value.substring(0, Math.min(value.length(), 2000));
    }

    private record RunRow(long id, long scheduleId, long projectId, String projectKey, String projectName,
                          String frequency, List<String> formats, boolean collectBeforeReport,
                          int collectionLimit, int topPostLimit, String timezone, String status,
                          Instant deadlineAt, String partialReason, Instant startedAt) {
    }

    private record CollectionOutcome(String query, String status, int recordCount,
                                     String errorCode, String errorMessage) {
    }
}
