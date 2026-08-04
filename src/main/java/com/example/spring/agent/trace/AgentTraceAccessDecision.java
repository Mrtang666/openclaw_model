package com.example.spring.agent.trace;

public record AgentTraceAccessDecision(boolean allowed, String reason) {

    public AgentTraceAccessDecision {
        reason = reason == null ? "" : reason.strip();
    }
}
