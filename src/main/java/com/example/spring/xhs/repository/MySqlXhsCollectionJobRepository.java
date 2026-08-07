package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;
import com.example.spring.xhs.model.XhsImportResult;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.source.XhsCollectionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

@Repository
public class MySqlXhsCollectionJobRepository implements XhsCollectionJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Duration claimTimeout;

    @Autowired
    public MySqlXhsCollectionJobRepository(JdbcTemplate jdbcTemplate,
                                           @Value("${xhs.collector.claim-timeout:5m}") Duration claimTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.claimTimeout = claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                ? Duration.ofMinutes(5) : claimTimeout;
    }

    @Override
    public void create(String jobKey, long projectId, XhsSourceType sourceType, String query, Instant now) {
        create(jobKey, projectId, sourceType, query, 20, "", now);
    }

    @Override
    public void create(String jobKey, long projectId, XhsSourceType sourceType, String query,
                       int requestedLimit, String cursor, Instant now) {
        create(jobKey, projectId, sourceType, query, requestedLimit, cursor,
                "GENERAL", "ANY", "ALL", now);
    }

    @Override
    public void create(String jobKey, long projectId, XhsSourceType sourceType, String query,
                       int requestedLimit, String cursor, String sortMode,
                       String timeRange, String noteType, Instant now) {
        create(jobKey, projectId, sourceType, query, requestedLimit, cursor,
                sortMode, timeRange, noteType, 100, now);
    }

    @Override
    public void create(String jobKey, long projectId, XhsSourceType sourceType, String query,
                       int requestedLimit, String cursor, String sortMode,
                       String timeRange, String noteType, int commentLimit, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO xhs_collection_jobs(
                            job_key, project_id, source_type, query_text, status, complete,
                            attempt_count, record_count, started_at)
                        VALUES (?, ?, ?, ?, 'PENDING', 0, 0, 0, ?)
                        """,
                jobKey, projectId, sourceType.name(), query, Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO xhs_search_executions(
                    job_key, project_id, keyword_value, keyword_type, query_mode, sort_mode,
                    time_range, note_type, cursor_start, requested_limit, requested_comment_limit,
                    status, completeness_status,
                    started_at, created_at, updated_at)
                VALUES (?, ?, ?, 'MANUAL', 'STANDARD', ?, ?, ?, ?, ?, ?, 'PENDING',
                        'NOT_STARTED', ?, ?, ?)
                """, jobKey, projectId, query, sortMode, timeRange, noteType, nullable(cursor),
                Math.max(1, Math.min(requestedLimit, 100)),
                Math.max(0, Math.min(commentLimit, 1000)), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void markSubmitted(String jobKey, String externalJobId) {
        jdbcTemplate.update("""
                        UPDATE xhs_collection_jobs
                        SET external_job_id = ?, status = 'SUBMITTED'
                        WHERE job_key = ? AND status = 'PENDING'
                        """,
                externalJobId, jobKey);
        jdbcTemplate.update("""
                UPDATE xhs_search_executions SET status = 'SUBMITTED',
                    completeness_status = 'NOT_STARTED', updated_at = CURRENT_TIMESTAMP(3)
                WHERE job_key = ?
                """, jobKey);
    }

    @Override
    public void recordImportStats(String jobKey, XhsImportResult result) {
        if (result == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE xhs_search_executions
                SET raw_count = ?, imported_count = ?, comment_count = ?, skipped_count = ?,
                    completeness_status = CASE WHEN skipped_count > 0 THEN 'PARTIAL' ELSE completeness_status END,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE job_key = ?
                """, result.postCount() + result.skippedCount(), result.postCount(),
                result.commentCount(), result.skippedCount(), jobKey);
    }

    @Override
    public List<XhsCollectionJob> findPending(int limit) {
        return jdbcTemplate.query("""
                        SELECT j.job_key, j.project_id, p.project_key, p.name AS project_name,
                               j.source_type, j.query_text, j.external_job_id, j.status,
                               j.attempt_count, j.started_at
                        FROM xhs_collection_jobs j
                        JOIN xhs_monitor_projects p ON p.id = j.project_id
                        WHERE j.status IN ('SUBMITTED', 'RUNNING')
                          AND (j.next_poll_at IS NULL OR j.next_poll_at <= CURRENT_TIMESTAMP(3))
                        ORDER BY j.started_at, j.id
                        LIMIT ?
                        """,
                this::mapJob,
                Math.max(1, limit));
    }

    @Override
    public void recordPoll(String jobKey, XhsCollectionStatus status) {
        jdbcTemplate.update("""
                        UPDATE xhs_collection_jobs
                        SET status = ?, attempt_count = attempt_count + 1,
                            last_polled_at = CURRENT_TIMESTAMP(3), next_poll_at = NULL
                        WHERE job_key = ? AND status IN ('SUBMITTED', 'RUNNING')
                        """,
                status.name(), jobKey);
        updateSearchRunning(jobKey, status, null);
    }

    @Override
    public List<XhsCollectionClaim> claimPending(int limit) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Timestamp staleBefore = Timestamp.from(now.minus(claimTimeout));
        jdbcTemplate.update("""
                UPDATE xhs_collection_jobs
                SET poll_lock_token = ?, poll_locked_at = ?
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id FROM xhs_collection_jobs
                        WHERE status IN ('SUBMITTED', 'RUNNING')
                          AND (next_poll_at IS NULL OR next_poll_at <= ?)
                          AND (poll_lock_token IS NULL OR poll_locked_at < ?)
                        ORDER BY started_at, id LIMIT ?
                    ) claimable
                )
                  AND (poll_lock_token IS NULL OR poll_locked_at < ?)
                """, token, Timestamp.from(now), Timestamp.from(now), staleBefore,
                Math.max(1, Math.min(limit, 100)), staleBefore);
        return jdbcTemplate.query("""
                SELECT j.job_key, j.project_id, p.project_key, p.name AS project_name,
                       j.source_type, j.query_text, j.external_job_id, j.status,
                       j.attempt_count, j.started_at
                FROM xhs_collection_jobs j
                JOIN xhs_monitor_projects p ON p.id = j.project_id
                WHERE j.poll_lock_token = ?
                ORDER BY j.started_at, j.id
                """, (rs, row) -> new XhsCollectionClaim(mapJob(rs, row), token), token);
    }

    @Override
    public void releaseClaim(XhsCollectionClaim claim) {
        jdbcTemplate.update("""
                UPDATE xhs_collection_jobs SET poll_lock_token = NULL, poll_locked_at = NULL
                WHERE job_key = ? AND poll_lock_token = ?
                """, claim.job().jobKey(), claim.token());
    }

    @Override
    public void recordPoll(String jobKey, XhsCollectionStatus status, String errorMessage, Instant nextPollAt) {
        String error = nullable(errorMessage);
        jdbcTemplate.update("""
                UPDATE xhs_collection_jobs
                SET status = ?, attempt_count = attempt_count + 1,
                    error_code = CASE WHEN ? IS NULL THEN NULL ELSE 'POLL_TRANSIENT' END,
                    error_message = ?,
                    last_polled_at = CURRENT_TIMESTAMP(3), next_poll_at = ?
                WHERE job_key = ? AND status IN ('SUBMITTED', 'RUNNING')
                """, status.name(), error, error, Timestamp.from(nextPollAt), jobKey);
        updateSearchRunning(jobKey, status, error);
    }

    @Override
    public void recordPoll(XhsCollectionClaim claim, XhsCollectionStatus status,
                           String errorMessage, Instant nextPollAt) {
        String error = nullable(errorMessage);
        jdbcTemplate.update("""
                UPDATE xhs_collection_jobs
                SET status = ?, attempt_count = attempt_count + 1,
                    error_code = CASE WHEN ? IS NULL THEN NULL ELSE 'POLL_TRANSIENT' END,
                    error_message = ?,
                    last_polled_at = CURRENT_TIMESTAMP(3), next_poll_at = ?,
                    poll_lock_token = NULL, poll_locked_at = NULL
                WHERE job_key = ? AND poll_lock_token = ?
                  AND status IN ('SUBMITTED', 'RUNNING')
                """, status.name(), error, error, Timestamp.from(nextPollAt),
                claim.job().jobKey(), claim.token());
        updateSearchRunning(claim.job().jobKey(), status, error);
    }

    @Override
    public void finish(
            String jobKey,
            XhsCollectionStatus status,
            boolean complete,
            int recordCount,
            String nextCursor,
            String errorCode,
            String errorMessage,
            Instant finishedAt) {
        jdbcTemplate.update("""
                        UPDATE xhs_collection_jobs
                        SET status = ?, complete = ?, record_count = ?, next_cursor = ?,
                            error_code = ?, error_message = ?, finished_at = ?,
                            attempt_count = attempt_count + 1
                        WHERE job_key = ? AND status IN ('PENDING', 'SUBMITTED', 'RUNNING')
                        """,
                status.name(), complete, Math.max(0, recordCount), nullable(nextCursor),
                nullable(errorCode), nullable(errorMessage), Timestamp.from(finishedAt), jobKey);
        finishSearch(jobKey, status, complete, nextCursor, errorCode, errorMessage, finishedAt);
    }

    @Override
    public void finish(XhsCollectionClaim claim, XhsCollectionStatus status, boolean complete,
                       int recordCount, String nextCursor, String errorCode,
                       String errorMessage, Instant finishedAt) {
        jdbcTemplate.update("""
                UPDATE xhs_collection_jobs
                SET status = ?, complete = ?, record_count = ?, next_cursor = ?,
                    error_code = ?, error_message = ?, finished_at = ?,
                    attempt_count = attempt_count + 1,
                    poll_lock_token = NULL, poll_locked_at = NULL
                WHERE job_key = ? AND poll_lock_token = ?
                  AND status IN ('SUBMITTED', 'RUNNING')
                """, status.name(), complete, Math.max(0, recordCount), nullable(nextCursor),
                nullable(errorCode), nullable(errorMessage), Timestamp.from(finishedAt),
                claim.job().jobKey(), claim.token());
        finishSearch(claim.job().jobKey(), status, complete, nextCursor, errorCode, errorMessage, finishedAt);
    }

    private void updateSearchRunning(String jobKey, XhsCollectionStatus status, String error) {
        jdbcTemplate.update("""
                UPDATE xhs_search_executions
                SET status = ?, completeness_status = 'PARTIAL', error_message = ?,
                    updated_at = CURRENT_TIMESTAMP(3) WHERE job_key = ?
                """, status.name(), error, jobKey);
    }

    private void finishSearch(String jobKey, XhsCollectionStatus status, boolean complete,
                              String nextCursor, String errorCode, String errorMessage,
                              Instant finishedAt) {
        String completeness = complete && status == XhsCollectionStatus.SUCCEEDED ? "FULL"
                : status == XhsCollectionStatus.FAILED ? "FAILED" : "PARTIAL";
        jdbcTemplate.update("""
                UPDATE xhs_search_executions
                SET status = ?, completeness_status = ?, cursor_end = ?, error_code = ?,
                    error_message = ?, finished_at = ?, updated_at = ? WHERE job_key = ?
                """, status.name(), completeness, nullable(nextCursor), nullable(errorCode),
                nullable(errorMessage), Timestamp.from(finishedAt), Timestamp.from(finishedAt), jobKey);
    }

    private XhsCollectionJob mapJob(ResultSet rs, int rowNumber) throws SQLException {
        return new XhsCollectionJob(
                rs.getString("job_key"),
                rs.getLong("project_id"),
                rs.getString("project_key"),
                rs.getString("project_name"),
                XhsSourceType.from(rs.getString("source_type")),
                rs.getString("query_text"),
                rs.getString("external_job_id"),
                XhsCollectionStatus.from(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getTimestamp("started_at").toInstant());
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
