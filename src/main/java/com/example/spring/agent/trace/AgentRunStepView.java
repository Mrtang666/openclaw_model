package com.example.spring.agent.trace;

import java.time.Instant;

public record AgentRunStepView(
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

    public AgentRunStepView {
        toolName = clean(toolName);
        inputSummary = clean(inputSummary);
        outputSummary = clean(outputSummary);
        metadataJson = clean(metadataJson);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
