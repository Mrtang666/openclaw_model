package com.example.spring.agent.trace;

import java.time.Instant;
import java.util.List;

public record AgentRunTraceView(
        long runId,
        String runKey,
        String channel,
        String sessionKey,
        String userText,
        String contextSummary,
        AgentRunStatus status,
        String stopReason,
        String finalReplySummary,
        Instant startedAt,
        Instant completedAt,
        List<AgentRunStepView> steps) {

    public AgentRunTraceView {
        runKey = clean(runKey);
        channel = clean(channel);
        sessionKey = clean(sessionKey);
        userText = clean(userText);
        contextSummary = clean(contextSummary);
        stopReason = clean(stopReason);
        finalReplySummary = clean(finalReplySummary);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
