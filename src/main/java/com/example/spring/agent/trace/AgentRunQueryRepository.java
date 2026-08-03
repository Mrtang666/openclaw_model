package com.example.spring.agent.trace;

import java.util.List;
import java.util.Optional;

public interface AgentRunQueryRepository {

    Optional<AgentRunTraceView> findRun(String runKey);

    List<AgentRunSummaryView> findRecentRuns(String sessionKey, int limit);
}
