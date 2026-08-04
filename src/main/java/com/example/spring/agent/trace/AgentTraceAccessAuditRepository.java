package com.example.spring.agent.trace;

public interface AgentTraceAccessAuditRepository {

    void record(AgentTraceAccessAuditEvent event);
}
