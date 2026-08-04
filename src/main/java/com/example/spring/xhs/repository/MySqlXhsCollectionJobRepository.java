package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;
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
        jdbcTemplate.update("""
                        INSERT INTO xhs_collection_jobs(
                            job_key, project_id, source_type, query_text, status, complete,
                            attempt_count, record_count, started_at)
                        VALUES (?, ?, ?, ?, 'PENDING', 0, 0, 0, ?)
                        """,
                jobKey, projectId, sourceType.name(), query, Timestamp.from(now));
    }

    @Override
    public void markSubmitted(String jobKey, String externalJobId) {
        jdbcTemplate.update("""
                        UPDATE xhs_collection_jobs
                        SET external_job_id = ?, status = 'SUBMITTED'
                        WHERE job_key = ? AND status = 'PENDING'
                        """,
                externalJobId, jobKey);
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
