package com.example.spring.agent.goal;

import java.time.Instant;
import java.util.List;

public interface AgentGoalRepository {

    AgentGoalHandle create(AgentGoalDraft draft);

    void updateStatus(AgentGoalHandle handle, AgentGoalStatus status, String resultSummary, Instant finishedAt);

    void recordStep(AgentGoalHandle handle, AgentGoalStepDraft draft);

    void recordEvaluation(AgentGoalHandle handle, AgentGoalEvaluationDraft draft);

    void recordReviewAction(AgentGoalHandle handle, AgentGoalReviewActionDraft draft);

    List<AgentGoalReviewAction> findPendingReviewActions(int limit);

    void updateReviewActionStatus(long actionId, AgentGoalReviewActionStatus status, Instant updatedAt);
}
