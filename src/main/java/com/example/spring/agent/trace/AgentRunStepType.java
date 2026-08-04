package com.example.spring.agent.trace;

public enum AgentRunStepType {
    MODEL_ROUND,
    TOOL_CALL,
    TOOL_RESULT,
    POLICY_DECISION,
    STOP
}
