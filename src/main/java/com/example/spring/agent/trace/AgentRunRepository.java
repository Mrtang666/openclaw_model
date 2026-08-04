package com.example.spring.agent.trace;

import java.util.Map;

public interface AgentRunRepository {

    AgentRunHandle createRun(String channel, String sessionKey, String userText, String contextSummary);

    void appendStep(
            AgentRunHandle handle,
            AgentRunStepType stepType,
            AgentRunStepStatus status,
            int roundNumber,
            String toolName,
            String inputSummary,
            String outputSummary,
            Map<String, ?> metadata);

    void completeRun(
            AgentRunHandle handle,
            AgentRunStatus status,
            String stopReason,
            String finalReplySummary);
}
