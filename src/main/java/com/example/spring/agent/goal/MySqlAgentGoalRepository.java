package com.example.spring.agent.goal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlAgentGoalRepository implements AgentGoalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlAgentGoalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentGoalHandle create(AgentGoalDraft draft) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertGoal(draft), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回 Agent Goal 主键");
        }
        return new AgentGoalHandle(key.longValue());
    }

    @Override
    public void updateStatus(AgentGoalHandle handle, AgentGoalStatus status, String resultSummary, Instant finishedAt) {
        Instant time = finishedAt == null ? Instant.now() : finishedAt;
        jdbcTemplate.update(
                """
                        UPDATE agent_goals
                        SET status = ?, result_summary = ?, updated_at = ?, finished_at = ?
                        WHERE id = ?
                        """,
                status == null ? AgentGoalStatus.FAILED.name() : status.name(),
                truncate(resultSummary, 2_000),
                Timestamp.from(time),
                Timestamp.from(time),
                handle.goalId());
    }

    @Override
    public void recordStep(AgentGoalHandle handle, AgentGoalStepDraft draft) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO agent_goal_steps
                            (goal_id, tool_name, arguments_json, result_summary, status, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    handle.goalId(),
                    draft.toolName(),
                    objectMapper.writeValueAsString(draft.arguments()),
                    truncate(draft.resultSummary(), 2_000),
                    truncate(draft.status(), 32),
                    Timestamp.from(draft.createdAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Agent Goal Step 参数无法序列化", exception);
        }
    }

    @Override
    public void recordEvaluation(AgentGoalHandle handle, AgentGoalEvaluationDraft draft) {
        jdbcTemplate.update(
                """
                        INSERT INTO agent_goal_evaluations
                        (goal_id, evaluator_name, status, reasoning, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                handle.goalId(),
                truncate(draft.evaluatorName(), 64),
                draft.status().name(),
                truncate(draft.reasoning(), 2_000),
                Timestamp.from(draft.createdAt()));
    }

    @Override
    public void recordReviewAction(AgentGoalHandle handle, AgentGoalReviewActionDraft draft) {
        Timestamp createdAt = Timestamp.from(draft.createdAt());
        jdbcTemplate.update(
                """
                        INSERT INTO agent_goal_review_actions
                        (goal_id, action_type, status, reason, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                handle.goalId(),
                draft.actionType().name(),
                draft.status().name(),
                truncate(draft.reason(), 2_000),
                createdAt,
                createdAt);
    }

    @Override
    public List<AgentGoalReviewAction> findPendingReviewActions(int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, goal_id, action_type, status, reason, created_at, updated_at
                        FROM agent_goal_review_actions
                        WHERE status = ?
                        ORDER BY created_at ASC, id ASC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new AgentGoalReviewAction(
                        resultSet.getLong("id"),
                        resultSet.getLong("goal_id"),
                        AgentGoalReviewActionType.valueOf(resultSet.getString("action_type")),
                        AgentGoalReviewActionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                AgentGoalReviewActionStatus.PENDING.name(),
                Math.max(1, limit));
    }

    @Override
    public void updateReviewActionStatus(long actionId, AgentGoalReviewActionStatus status, Instant updatedAt) {
        Instant time = updatedAt == null ? Instant.now() : updatedAt;
        jdbcTemplate.update(
                """
                        UPDATE agent_goal_review_actions
                        SET status = ?, updated_at = ?
                        WHERE id = ?
                        """,
                status == null ? AgentGoalReviewActionStatus.PENDING.name() : status.name(),
                Timestamp.from(time),
                actionId);
    }

    private PreparedStatementCreator insertGoal(AgentGoalDraft draft) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO agent_goals
                            (channel, session_key, goal_type, objective, status, started_at, updated_at, finished_at, result_summary)
                            VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            Timestamp startedAt = Timestamp.from(draft.startedAt());
            statement.setString(1, draft.channel());
            statement.setString(2, draft.sessionKey());
            statement.setString(3, draft.goalType());
            statement.setString(4, truncate(draft.objective(), 2_000));
            statement.setString(5, draft.status().name());
            statement.setTimestamp(6, startedAt);
            statement.setTimestamp(7, startedAt);
            return statement;
        };
    }

    private String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
