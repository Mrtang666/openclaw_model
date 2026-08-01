package com.example.spring.agent.goal;

import java.time.Instant;

public record AgentGoalReviewAction(
        long id,
        long goalId,
        AgentGoalReviewActionType actionType,
        AgentGoalReviewActionStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt) {

    public AgentGoalReviewAction {
        actionType = actionType == null ? AgentGoalReviewActionType.RETRY : actionType;
        status = status == null ? AgentGoalReviewActionStatus.PENDING : status;
        reason = reason == null ? "" : reason.strip();
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
}
