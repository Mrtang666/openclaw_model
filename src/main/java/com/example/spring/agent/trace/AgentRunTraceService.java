package com.example.spring.agent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentRunTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunTraceService.class);

    private final AgentRunRepository repository;

    public AgentRunTraceService(AgentRunRepository repository) {
        this.repository = repository;
    }

    public AgentRunHandle startWechatRun(String sessionKey, String userText, String contextSummary) {
        try {
            if (repository == null) {
                return AgentRunHandle.noop();
            }
            return repository.createRun("WECHAT", sessionKey, userText, contextSummary);
        } catch (RuntimeException exception) {
            log.warn("Agent run trace start failed, sessionKey={}, error={}", safe(sessionKey), rootMessage(exception));
            return AgentRunHandle.noop();
        }
    }

    public void recordModelRound(
            AgentRunHandle handle,
            int roundNumber,
            String inputSummary,
            String outputSummary,
            Map<String, ?> metadata) {
        appendStep(
                handle,
                AgentRunStepType.MODEL_ROUND,
                AgentRunStepStatus.SUCCESS,
                roundNumber,
                "",
                inputSummary,
                outputSummary,
                metadata);
    }

    public void recordToolCall(
            AgentRunHandle handle,
            int roundNumber,
            String toolName,
            String inputSummary) {
        appendStep(
                handle,
                AgentRunStepType.TOOL_CALL,
                AgentRunStepStatus.STARTED,
                roundNumber,
                toolName,
                inputSummary,
                "",
                Map.of());
    }

    public void recordToolResult(
            AgentRunHandle handle,
            int roundNumber,
            String toolName,
            AgentRunStepStatus status,
            String inputSummary,
            String outputSummary) {
        appendStep(
                handle,
                AgentRunStepType.TOOL_RESULT,
                status,
                roundNumber,
                toolName,
                inputSummary,
                outputSummary,
                Map.of());
    }

    public void recordPolicyDecision(
            AgentRunHandle handle,
            int roundNumber,
            String toolName,
            AgentRunStepStatus status,
            String decisionType,
            String inputSummary,
            String outputSummary,
            Map<String, ?> metadata) {
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        mergedMetadata.put("decision_type", safe(decisionType));
        if (metadata != null && !metadata.isEmpty()) {
            mergedMetadata.putAll(metadata);
        }
        appendStep(
                handle,
                AgentRunStepType.POLICY_DECISION,
                status,
                roundNumber,
                toolName,
                inputSummary,
                outputSummary,
                mergedMetadata);
    }

    public void complete(
            AgentRunHandle handle,
            AgentRunStatus status,
            String stopReason,
            String finalReplySummary) {
        try {
            if (repository != null && handle != null && handle.active()) {
                repository.completeRun(handle, status, stopReason, finalReplySummary);
            }
        } catch (RuntimeException exception) {
            log.warn("Agent run trace complete failed, runKey={}, error={}",
                    handle == null ? "" : handle.runKey(), rootMessage(exception));
        }
    }

    private void appendStep(
            AgentRunHandle handle,
            AgentRunStepType stepType,
            AgentRunStepStatus status,
            int roundNumber,
            String toolName,
            String inputSummary,
            String outputSummary,
            Map<String, ?> metadata) {
        try {
            if (repository != null && handle != null && handle.active()) {
                repository.appendStep(
                        handle,
                        stepType,
                        status,
                        roundNumber,
                        toolName,
                        inputSummary,
                        outputSummary,
                        metadata);
            }
        } catch (RuntimeException exception) {
            log.warn("Agent run trace step failed, runKey={}, stepType={}, error={}",
                    handle == null ? "" : handle.runKey(), stepType, rootMessage(exception));
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
