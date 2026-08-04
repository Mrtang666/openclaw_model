package com.example.spring.agent.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcAgentRunRepository implements AgentRunRepository {

    private static final int MAX_TEXT_LENGTH = 8_000;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcAgentRunRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC(), new ObjectMapper());
    }

    JdbcAgentRunRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this(jdbcTemplate, clock, new ObjectMapper());
    }

    JdbcAgentRunRepository(JdbcTemplate jdbcTemplate, Clock clock, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public AgentRunHandle createRun(String channel, String sessionKey, String userText, String contextSummary) {
        Instant now = Instant.now(clock);
        String runKey = "agent-run-" + UUID.randomUUID();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertRunStatement(runKey, channel, sessionKey, userText, contextSummary, now), keyHolder);
        Number key = keyHolder.getKey();
        return new AgentRunHandle(key == null ? 0 : key.longValue(), runKey);
    }

    @Override
    public void appendStep(
            AgentRunHandle handle,
            AgentRunStepType stepType,
            AgentRunStepStatus status,
            int roundNumber,
            String toolName,
            String inputSummary,
            String outputSummary,
            Map<String, ?> metadata) {
        if (handle == null || !handle.active()) {
            return;
        }
        Integer nextIndex = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(step_index), 0) + 1 FROM agent_run_steps WHERE run_id = ?",
                Integer.class,
                handle.runId());
        jdbcTemplate.update(
                """
                        INSERT INTO agent_run_steps
                        (run_id, step_index, step_type, round_number, tool_name, status,
                         input_summary, output_summary, metadata_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                handle.runId(),
                nextIndex == null ? 1 : nextIndex,
                safeEnum(stepType, AgentRunStepType.MODEL_ROUND),
                roundNumber <= 0 ? null : roundNumber,
                blankToNull(toolName),
                safeEnum(status, AgentRunStepStatus.SUCCESS),
                truncate(inputSummary),
                truncate(outputSummary),
                metadataJson(metadata),
                Timestamp.from(Instant.now(clock)));
    }

    @Override
    public void completeRun(
            AgentRunHandle handle,
            AgentRunStatus status,
            String stopReason,
            String finalReplySummary) {
        if (handle == null || !handle.active()) {
            return;
        }
        jdbcTemplate.update(
                """
                        UPDATE agent_runs
                        SET status = ?, stop_reason = ?, final_reply_summary = ?, completed_at = ?
                        WHERE id = ?
                        """,
                safeEnum(status, AgentRunStatus.STOPPED),
                blankToNull(stopReason),
                truncate(finalReplySummary),
                Timestamp.from(Instant.now(clock)),
                handle.runId());
    }

    private PreparedStatementCreator insertRunStatement(
            String runKey,
            String channel,
            String sessionKey,
            String userText,
            String contextSummary,
            Instant now) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO agent_runs
                            (run_key, channel, session_key, user_text, context_summary,
                             status, started_at, completed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, runKey);
            statement.setString(2, clean(channel).isBlank() ? "UNKNOWN" : clean(channel));
            statement.setString(3, clean(sessionKey));
            statement.setString(4, truncate(userText));
            statement.setString(5, truncate(contextSummary));
            statement.setString(6, AgentRunStatus.RUNNING.name());
            statement.setTimestamp(7, Timestamp.from(now));
            return statement;
        };
    }

    private String metadataJson(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String safeEnum(Enum<?> value, Enum<?> fallback) {
        return value == null ? fallback.name() : value.name();
    }

    private String blankToNull(String value) {
        String text = clean(value);
        return text.isBlank() ? null : text;
    }

    private String truncate(String value) {
        String text = clean(value);
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
