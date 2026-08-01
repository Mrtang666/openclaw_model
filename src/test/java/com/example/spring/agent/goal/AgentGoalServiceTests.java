package com.example.spring.agent.goal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGoalServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void startWechatGoalCreatesRunningGoal() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalService service = new AgentGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        AgentGoalHandle handle = service.startWechatGoal("wx-user", "帮我生成今日舆情日报").orElseThrow();

        assertThat(handle.goalId()).isEqualTo(100L);
        assertThat(repository.created).hasSize(1);
        AgentGoalDraft draft = repository.created.get(0);
        assertThat(draft.channel()).isEqualTo("WECHAT");
        assertThat(draft.sessionKey()).isEqualTo("wx-user");
        assertThat(draft.objective()).isEqualTo("帮我生成今日舆情日报");
        assertThat(draft.goalType()).isEqualTo("wechat_message");
        assertThat(draft.status()).isEqualTo(AgentGoalStatus.RUNNING);
        assertThat(draft.startedAt()).isEqualTo(NOW);
    }

    @Test
    void completeAndFailUpdateGoalStatus() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalService service = new AgentGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AgentGoalHandle handle = new AgentGoalHandle(7L);

        service.complete(handle, "日报已经生成并发送");
        service.fail(handle, "模型没有返回可用回复");

        assertThat(repository.updates)
                .containsExactly(
                        new GoalUpdate(7L, AgentGoalStatus.COMPLETED, "日报已经生成并发送", NOW),
                        new GoalUpdate(7L, AgentGoalStatus.FAILED, "模型没有返回可用回复", NOW));
    }

    @Test
    void recordToolStepStoresStepUnderGoal() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalService service = new AgentGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AgentGoalHandle handle = new AgentGoalHandle(7L);

        service.recordToolStep(handle, "web_search", Map.of("query", "OpenClaw"), "搜索完成", "SUCCESS");

        assertThat(repository.steps).containsExactly(new StepRecord(
                7L,
                new AgentGoalStepDraft(
                        "web_search",
                        Map.of("query", "OpenClaw"),
                        "搜索完成",
                        "SUCCESS",
                        NOW)));
    }

    @Test
    void recordEvaluationStoresEvaluationUnderGoal() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalService service = new AgentGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AgentGoalHandle handle = new AgentGoalHandle(7L);

        service.recordEvaluation(handle, "rule-based", AgentGoalEvaluationStatus.PASSED, "reply contains user-visible content");

        assertThat(repository.evaluations).containsExactly(new EvaluationRecord(
                7L,
                new AgentGoalEvaluationDraft(
                        "rule-based",
                        AgentGoalEvaluationStatus.PASSED,
                        "reply contains user-visible content",
                        NOW)));
    }

    @Test
    void recordFailureReviewActionStoresPendingActionUnderGoal() {
        RecordingAgentGoalRepository repository = new RecordingAgentGoalRepository();
        AgentGoalService service = new AgentGoalService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        AgentGoalHandle handle = new AgentGoalHandle(7L);

        service.recordFailureReviewAction(handle, "Function Calling Agent Loop returned no user-visible reply");

        assertThat(repository.reviewActions).containsExactly(new ReviewActionRecord(
                7L,
                new AgentGoalReviewActionDraft(
                        AgentGoalReviewActionType.IMPROVE_PROMPT,
                        AgentGoalReviewActionStatus.PENDING,
                        "Function Calling Agent Loop returned no user-visible reply",
                        NOW)));
    }

    private static final class RecordingAgentGoalRepository implements AgentGoalRepository {

        private final List<AgentGoalDraft> created = new ArrayList<>();
        private final List<GoalUpdate> updates = new ArrayList<>();
        private final List<StepRecord> steps = new ArrayList<>();
        private final List<EvaluationRecord> evaluations = new ArrayList<>();
        private final List<ReviewActionRecord> reviewActions = new ArrayList<>();

        @Override
        public AgentGoalHandle create(AgentGoalDraft draft) {
            created.add(draft);
            return new AgentGoalHandle(100L);
        }

        @Override
        public void updateStatus(AgentGoalHandle handle, AgentGoalStatus status, String resultSummary, Instant finishedAt) {
            updates.add(new GoalUpdate(handle.goalId(), status, resultSummary, finishedAt));
        }

        @Override
        public void recordStep(AgentGoalHandle handle, AgentGoalStepDraft draft) {
            steps.add(new StepRecord(handle.goalId(), draft));
        }

        @Override
        public void recordEvaluation(AgentGoalHandle handle, AgentGoalEvaluationDraft draft) {
            evaluations.add(new EvaluationRecord(handle.goalId(), draft));
        }

        @Override
        public void recordReviewAction(AgentGoalHandle handle, AgentGoalReviewActionDraft draft) {
            reviewActions.add(new ReviewActionRecord(handle.goalId(), draft));
        }

        @Override
        public List<AgentGoalReviewAction> findPendingReviewActions(int limit) {
            return List.of();
        }

        @Override
        public void updateReviewActionStatus(long actionId, AgentGoalReviewActionStatus status, Instant updatedAt) {
        }
    }

    private record GoalUpdate(long goalId, AgentGoalStatus status, String resultSummary, Instant finishedAt) {
    }

    private record StepRecord(long goalId, AgentGoalStepDraft draft) {
    }

    private record EvaluationRecord(long goalId, AgentGoalEvaluationDraft draft) {
    }

    private record ReviewActionRecord(long goalId, AgentGoalReviewActionDraft draft) {
    }
}
