package com.example.spring.agent.trace;

public record AgentRunDiagnosticStatsView(
        int totalStepCount,
        int modelRoundCount,
        int toolCallCount,
        int failedStepCount,
        int skippedStepCount,
        int phaseCount) {

    public static AgentRunDiagnosticStatsView empty() {
        return new AgentRunDiagnosticStatsView(0, 0, 0, 0, 0, 0);
    }
}
