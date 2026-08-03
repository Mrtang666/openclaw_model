package com.example.spring.agent.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class AgentGoalTracker {

    private static final Logger log = LoggerFactory.getLogger(AgentGoalTracker.class);
    private static final String EVALUATOR_RULE_BASED = "rule-based";
    private static final String SUCCESS_REASON = "reply contains user-visible content";

    private final AgentGoalService service;
    private final AgentGoalHandle handle;

    private AgentGoalTracker(AgentGoalService service, AgentGoalHandle handle) {
        this.service = service;
        this.handle = handle;
    }

    public static AgentGoalTracker start(AgentGoalService service, String sessionKey, String objective) {
        if (service == null) {
            return noop();
        }
        try {
            Optional<AgentGoalHandle> handle = service.startWechatGoal(sessionKey, objective);
            return handle.map(value -> new AgentGoalTracker(service, value)).orElseGet(AgentGoalTracker::noop);
        } catch (RuntimeException exception) {
            log.warn("Agent Goal tracking start failed, sessionKey={}, error={}", safe(sessionKey), rootMessage(exception));
            return noop();
        }
    }

    public void recordToolStep(
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            String status) {
        if (!available()) {
            return;
        }
        service.recordToolStep(handle, toolName, arguments, resultSummary, status);
    }

    public void succeed(String summary) {
        if (!available()) {
            return;
        }
        service.complete(handle, summary);
        service.recordEvaluation(handle, EVALUATOR_RULE_BASED, AgentGoalEvaluationStatus.PASSED, SUCCESS_REASON);
    }

    public void fail(String userSummary, String machineReason) {
        if (!available()) {
            return;
        }
        service.fail(handle, userSummary);
        service.recordEvaluation(handle, EVALUATOR_RULE_BASED, AgentGoalEvaluationStatus.FAILED, machineReason);
        service.recordFailureReviewAction(handle, machineReason);
    }

    private boolean available() {
        return service != null && handle != null;
    }

    private static AgentGoalTracker noop() {
        return new AgentGoalTracker(null, null);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
