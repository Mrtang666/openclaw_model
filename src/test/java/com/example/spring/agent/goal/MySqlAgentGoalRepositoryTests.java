package com.example.spring.agent.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class MySqlAgentGoalRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentGoalRepository repository;

    @BeforeEach
    void cleanTables() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("openclaw_test");
        jdbcTemplate.update("DELETE FROM agent_goal_review_actions");
        jdbcTemplate.update("DELETE FROM agent_goal_evaluations");
        jdbcTemplate.update("DELETE FROM agent_goal_steps");
        jdbcTemplate.update("DELETE FROM agent_goals");
    }

    @Test
    void findsPendingReviewActionsInCreatedOrderAndUpdatesStatus() {
        AgentGoalHandle firstGoal = repository.create(new AgentGoalDraft(
                "WECHAT",
                "wx-1",
                "wechat_message",
                "first goal",
                AgentGoalStatus.FAILED,
                NOW.minusSeconds(30)));
        AgentGoalHandle secondGoal = repository.create(new AgentGoalDraft(
                "WECHAT",
                "wx-2",
                "wechat_message",
                "second goal",
                AgentGoalStatus.FAILED,
                NOW.minusSeconds(20)));
        long ignoredAppliedActionId = insertReviewAction(
                secondGoal.goalId(),
                AgentGoalReviewActionType.RETRY,
                AgentGoalReviewActionStatus.APPLIED,
                "already handled",
                NOW.minusSeconds(15));
        long firstPendingActionId = insertReviewAction(
                firstGoal.goalId(),
                AgentGoalReviewActionType.IMPROVE_PROMPT,
                AgentGoalReviewActionStatus.PENDING,
                "no reply",
                NOW.minusSeconds(10));
        long secondPendingActionId = insertReviewAction(
                secondGoal.goalId(),
                AgentGoalReviewActionType.RETRY,
                AgentGoalReviewActionStatus.PENDING,
                "tool failed",
                NOW);

        List<AgentGoalReviewAction> actions = repository.findPendingReviewActions(10);

        assertThat(actions).extracting(AgentGoalReviewAction::id)
                .containsExactly(firstPendingActionId, secondPendingActionId);
        assertThat(actions).extracting(AgentGoalReviewAction::goalId)
                .containsExactly(firstGoal.goalId(), secondGoal.goalId());
        assertThat(actions).extracting(AgentGoalReviewAction::actionType)
                .containsExactly(AgentGoalReviewActionType.IMPROVE_PROMPT, AgentGoalReviewActionType.RETRY);
        assertThat(actions).extracting(AgentGoalReviewAction::reason)
                .containsExactly("no reply", "tool failed");
        assertThat(actions).extracting(AgentGoalReviewAction::id)
                .doesNotContain(ignoredAppliedActionId);

        repository.updateReviewActionStatus(firstPendingActionId, AgentGoalReviewActionStatus.APPLIED, NOW.plusSeconds(5));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_goal_review_actions WHERE id = ?",
                String.class,
                firstPendingActionId)).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM agent_goal_review_actions WHERE id = ?",
                Timestamp.class,
                firstPendingActionId).toInstant()).isEqualTo(NOW.plusSeconds(5));
    }

    private long insertReviewAction(
            long goalId,
            AgentGoalReviewActionType actionType,
            AgentGoalReviewActionStatus status,
            String reason,
            Instant createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO agent_goal_review_actions
                        (goal_id, action_type, status, reason, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                goalId,
                actionType.name(),
                status.name(),
                reason,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
