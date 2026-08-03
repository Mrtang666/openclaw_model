package com.example.spring.agent.trace;

import java.time.Instant;

public record AgentRunDiagnosticSummaryView(
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

    public AgentRunDiagnosticSummaryView {
        stats = stats == null ? AgentRunDiagnosticStatsView.empty() : stats;
    }
}
