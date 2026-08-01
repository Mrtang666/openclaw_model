package com.example.spring.agent.goal;

import java.time.Instant;

public record AgentGoalReviewActionDraft(
        AgentGoalReviewActionType actionType,
        AgentGoalReviewActionStatus status,
        String reason,
        Instant createdAt) {

    public AgentGoalReviewActionDraft {
        actionType = actionType == null ? AgentGoalReviewActionType.RETRY : actionType;
        status = status == null ? AgentGoalReviewActionStatus.PENDING : status;
        reason = safe(reason);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
