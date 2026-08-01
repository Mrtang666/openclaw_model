package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.source.XhsCollectionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlXhsCollectionJobRepository implements XhsCollectionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlXhsCollectionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                        SET status = ?, attempt_count = attempt_count + 1
                        WHERE job_key = ? AND status IN ('SUBMITTED', 'RUNNING')
                        """,
                status.name(), jobKey);
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
