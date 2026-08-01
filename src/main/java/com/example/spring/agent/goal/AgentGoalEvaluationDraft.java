package com.example.spring.agent.goal;

import java.time.Instant;

public record AgentGoalEvaluationDraft(
        String evaluatorName,
        AgentGoalEvaluationStatus status,
        String reasoning,
        Instant createdAt) {

    public AgentGoalEvaluationDraft {
        evaluatorName = safe(evaluatorName).isBlank() ? "unknown" : safe(evaluatorName);
        status = status == null ? AgentGoalEvaluationStatus.NEEDS_REVIEW : status;
        reasoning = safe(reasoning);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
