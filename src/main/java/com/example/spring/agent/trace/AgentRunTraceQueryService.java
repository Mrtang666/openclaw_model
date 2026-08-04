package com.example.spring.agent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentRunTraceQueryService {

    static final int DEFAULT_RECENT_RUN_LIMIT = 20;
    static final int MAX_RECENT_RUN_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(AgentRunTraceQueryService.class);

    private final AgentRunQueryRepository repository;

    public AgentRunTraceQueryService(AgentRunQueryRepository repository) {
        this.repository = repository;
    }

    public Optional<AgentRunTraceView> findRun(String runKey) {
        String cleanRunKey = clean(runKey);
        if (cleanRunKey.isBlank() || repository == null) {
            return Optional.empty();
        }
        try {
            return repository.findRun(cleanRunKey);
        } catch (RuntimeException exception) {
            log.warn("Agent run trace query failed, runKey={}, error={}", cleanRunKey, rootMessage(exception));
            return Optional.empty();
        }
    }

    public List<AgentRunSummaryView> findRecentRuns(String sessionKey, int limit) {
        String cleanSessionKey = clean(sessionKey);
        if (cleanSessionKey.isBlank() || repository == null) {
            return List.of();
        }
        int normalizedLimit = normalizeLimit(limit);
        try {
            return repository.findRecentRuns(cleanSessionKey, normalizedLimit);
        } catch (RuntimeException exception) {
            log.warn("Agent run recent trace query failed, sessionKey={}, limit={}, error={}",
                    cleanSessionKey, normalizedLimit, rootMessage(exception));
            return List.of();
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RECENT_RUN_LIMIT;
        }
        return Math.min(limit, MAX_RECENT_RUN_LIMIT);
    }

    private String clean(String value) {
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
