package com.example.spring.agent.goal;

import java.time.Instant;
import java.util.Map;

public record AgentGoalStepDraft(
        String toolName,
        Map<String, String> arguments,
        String resultSummary,
        String status,
        Instant createdAt) {

    public AgentGoalStepDraft {
        toolName = safe(toolName);
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        resultSummary = safe(resultSummary);
        status = safe(status).isBlank() ? "UNKNOWN" : safe(status);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
