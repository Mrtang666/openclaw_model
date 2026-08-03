package com.example.spring.agent.trace;

public record AgentRunHandle(long runId, String runKey) {

    public static AgentRunHandle noop() {
        return new AgentRunHandle(0, "");
    }

    public AgentRunHandle {
        runKey = runKey == null ? "" : runKey.strip();
    }

    public boolean active() {
        return runId > 0 && !runKey.isBlank();
    }
}
