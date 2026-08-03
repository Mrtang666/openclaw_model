package com.example.spring.agent.trace;

import java.time.Instant;
import java.util.List;

public record AgentRunDiagnosticTraceView(
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
        AgentRunDiagnosticStatsView stats,
        List<AgentRunDiagnosticPhaseView> phases,
        List<AgentRunDiagnosticStepView> steps) {

    public AgentRunDiagnosticTraceView {
        stats = stats == null ? AgentRunDiagnosticStatsView.empty() : stats;
        phases = phases == null ? List.of() : List.copyOf(phases);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
