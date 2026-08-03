package com.example.spring.agent.trace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAgentRunQueryRepository implements AgentRunQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRunQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AgentRunTraceView> findRun(String runKey) {
        String cleanRunKey = clean(runKey);
        if (cleanRunKey.isBlank()) {
            return Optional.empty();
        }
        List<AgentRunSummaryView> runs = jdbcTemplate.query(
                """
                        SELECT id, run_key, channel, session_key, user_text, context_summary,
                               status, stop_reason, final_reply_summary, started_at, completed_at
                        FROM agent_runs
                        WHERE run_key = ?
                        """,
                runSummaryMapper(),
                cleanRunKey);
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        AgentRunSummaryView run = runs.get(0);
        List<AgentRunStepView> steps = jdbcTemplate.query(
                """
                        SELECT id, step_index, step_type, round_number, tool_name, status,
                               input_summary, output_summary, metadata_json, created_at
                        FROM agent_run_steps
                        WHERE run_id = ?
                        ORDER BY step_index ASC
                        """,
                stepMapper(),
                run.runId());
        return Optional.of(new AgentRunTraceView(
                run.runId(),
                run.runKey(),
                run.channel(),
                run.sessionKey(),
                run.userText(),
                run.contextSummary(),
                run.status(),
                run.stopReason(),
                run.finalReplySummary(),
                run.startedAt(),
                run.completedAt(),
                steps));
    }

    @Override
    public List<AgentRunSummaryView> findRecentRuns(String sessionKey, int limit) {
        String cleanSessionKey = clean(sessionKey);
        if (cleanSessionKey.isBlank() || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT id, run_key, channel, session_key, user_text, context_summary,
                               status, stop_reason, final_reply_summary, started_at, completed_at
                        FROM agent_runs
                        WHERE session_key = ?
                        ORDER BY started_at DESC, id DESC
                        LIMIT ?
                        """,
                runSummaryMapper(),
                cleanSessionKey,
                limit);
    }

    private RowMapper<AgentRunSummaryView> runSummaryMapper() {
        return (rs, rowNum) -> new AgentRunSummaryView(
                rs.getLong("id"),
                rs.getString("run_key"),
                rs.getString("channel"),
                rs.getString("session_key"),
                rs.getString("user_text"),
                rs.getString("context_summary"),
                enumValue(AgentRunStatus.class, rs.getString("status"), AgentRunStatus.STOPPED),
                rs.getString("stop_reason"),
                rs.getString("final_reply_summary"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"));
    }

    private RowMapper<AgentRunStepView> stepMapper() {
        return (rs, rowNum) -> new AgentRunStepView(
                rs.getLong("id"),
                rs.getInt("step_index"),
                enumValue(AgentRunStepType.class, rs.getString("step_type"), AgentRunStepType.MODEL_ROUND),
                nullableInteger(rs, "round_number"),
                rs.getString("tool_name"),
                enumValue(AgentRunStepStatus.class, rs.getString("status"), AgentRunStepStatus.SUCCESS),
                rs.getString("input_summary"),
                rs.getString("output_summary"),
                rs.getString("metadata_json"),
                instant(rs, "created_at"));
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
