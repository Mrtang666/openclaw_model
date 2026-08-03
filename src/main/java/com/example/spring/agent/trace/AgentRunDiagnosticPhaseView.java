package com.example.spring.agent.trace;

public record AgentRunDiagnosticPhaseView(
        AgentRunStepPhase phase,
        int startStepIndex,
        int endStepIndex,
        int stepCount,
        AgentRunStepStatus status) {
}
