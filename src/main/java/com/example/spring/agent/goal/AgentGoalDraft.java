package com.example.spring.agent.goal;

import java.time.Instant;

public record AgentGoalDraft(
        String channel,
        String sessionKey,
        String goalType,
        String objective,
        AgentGoalStatus status,
        Instant startedAt) {

    public AgentGoalDraft {
        channel = safe(channel);
        sessionKey = safe(sessionKey);
        goalType = safe(goalType);
        objective = safe(objective);
        status = status == null ? AgentGoalStatus.RUNNING : status;
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
