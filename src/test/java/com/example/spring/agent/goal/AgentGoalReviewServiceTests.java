package com.example.spring.agent.goal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGoalReviewServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

    @Test
    void pendingActionsReturnsRepositoryActionsWithBoundedLimit() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalReviewAction first = new AgentGoalReviewAction(
                1L,
                10L,
                AgentGoalReviewActionType.IMPROVE_PROMPT,
                AgentGoalReviewActionStatus.PENDING,
                "no reply",
                NOW.minusSeconds(10),
                NOW.minusSeconds(10));
        AgentGoalReviewAction second = new AgentGoalReviewAction(
                2L,
                11L,
                AgentGoalReviewActionType.RETRY,
                AgentGoalReviewActionStatus.PENDING,
                "tool failed",
                NOW,
                NOW);
        repository.pendingActions.add(first);
        repository.pendingActions.add(second);
        AgentGoalReviewService service = new AgentGoalReviewService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        List<AgentGoalReviewAction> actions = service.pendingActions(1);

        assertThat(actions).containsExactly(first);
        assertThat(repository.lastPendingLimit).isEqualTo(1);
    }

    @Test
    void markAppliedAndDismissedUpdateActionStatus() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalReviewService service = new AgentGoalReviewService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        service.markApplied(7L);
        service.markDismissed(8L);

        assertThat(repository.statusUpdates).containsExactly(
                new ReviewActionStatusUpdate(7L, AgentGoalReviewActionStatus.APPLIED, NOW),
                new ReviewActionStatusUpdate(8L, AgentGoalReviewActionStatus.DISMISSED, NOW));
    }

    private static final class RecordingAgentGoalRepository implements AgentGoalRepository {

        private final List<AgentGoalReviewAction> pendingActions = new ArrayList<>();
        private final List<ReviewActionStatusUpdate> statusUpdates = new ArrayList<>();
        private int lastPendingLimit;

        @Override
        public AgentGoalHandle create(AgentGoalDraft draft) {
            return new AgentGoalHandle(100L);
        }

        @Override
        public void updateStatus(AgentGoalHandle handle, AgentGoalStatus status, String resultSummary, Instant finishedAt) {
        }

        @Override
        public void recordStep(AgentGoalHandle handle, AgentGoalStepDraft draft) {
        }

        @Override
        public void recordEvaluation(AgentGoalHandle handle, AgentGoalEvaluationDraft draft) {
        }

        @Override
        public void recordReviewAction(AgentGoalHandle handle, AgentGoalReviewActionDraft draft) {
        }

        @Override
        public List<AgentGoalReviewAction> findPendingReviewActions(int limit) {
            lastPendingLimit = limit;
            return pendingActions.stream().limit(limit).toList();
        }

        @Override
        public void updateReviewActionStatus(long actionId, AgentGoalReviewActionStatus status, Instant updatedAt) {
            statusUpdates.add(new ReviewActionStatusUpdate(actionId, status, updatedAt));
        }
    }

    private record ReviewActionStatusUpdate(
            long actionId,
            AgentGoalReviewActionStatus status,
            Instant updatedAt) {
    }
}
