package com.example.spring.agent.trace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcAgentTraceAccessAuditQueryRepository implements AgentTraceAccessAuditQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentTraceAccessAuditQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentTraceAccessAuditView> findRecentByTarget(String targetType, String targetKey, int limit) {
        if (isBlank(targetType) || isBlank(targetKey) || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT id, actor, action, target_type, target_key, allowed, reason,
                               remote_address, user_agent, created_at
                        FROM agent_trace_access_audit
                        WHERE target_type = ? AND target_key = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                mapper(),
                clean(targetType),
                clean(targetKey),
                limit);
    }

    @Override
    public List<AgentTraceAccessAuditView> findRecentByActor(String actor, int limit) {
        if (isBlank(actor) || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT id, actor, action, target_type, target_key, allowed, reason,
                               remote_address, user_agent, created_at
                        FROM agent_trace_access_audit
                        WHERE actor = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                mapper(),
                clean(actor),
                limit);
    }

    private RowMapper<AgentTraceAccessAuditView> mapper() {
        return (rs, rowNum) -> new AgentTraceAccessAuditView(
                rs.getLong("id"),
                rs.getString("actor"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_key"),
                rs.getBoolean("allowed"),
                rs.getString("reason"),
                rs.getString("remote_address"),
                rs.getString("user_agent"),
                instant(rs, "created_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean isBlank(String value) {
        return clean(value).isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
