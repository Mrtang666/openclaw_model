package com.example.spring.agent.trace;

import java.time.Instant;

public record AgentRunDiagnosticStepView(
        long stepId,
        int stepIndex,
        AgentRunStepType stepType,
        Integer roundNumber,
        String toolName,
        AgentRunStepStatus status,
        String inputSummary,
        String outputSummary,
        String metadataJson,
        Instant createdAt) {
}
