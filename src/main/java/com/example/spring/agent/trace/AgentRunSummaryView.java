package com.example.spring.agent.trace;

import java.time.Instant;

public record AgentRunSummaryView(
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
        AgentRunDiagnosticStatsView stats) {

    public AgentRunSummaryView {
        runKey = clean(runKey);
        channel = clean(channel);
        sessionKey = clean(sessionKey);
        userText = clean(userText);
        contextSummary = clean(contextSummary);
        stopReason = clean(stopReason);
        finalReplySummary = clean(finalReplySummary);
        stats = stats == null ? AgentRunDiagnosticStatsView.empty() : stats;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
