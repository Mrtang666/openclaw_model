package com.example.spring.agent.trace;

import java.util.List;

public interface AgentTraceAccessAuditQueryRepository {

    List<AgentTraceAccessAuditView> findRecentByTarget(String targetType, String targetKey, int limit);

    List<AgentTraceAccessAuditView> findRecentByActor(String actor, int limit);
}
