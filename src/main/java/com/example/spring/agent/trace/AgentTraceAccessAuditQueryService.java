package com.example.spring.agent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentTraceAccessAuditQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(AgentTraceAccessAuditQueryService.class);

    private final AgentTraceAccessAuditQueryRepository repository;

    public AgentTraceAccessAuditQueryService(AgentTraceAccessAuditQueryRepository repository) {
        this.repository = repository;
    }

    public List<AgentTraceAccessAuditView> findRecentByTarget(String targetType, String targetKey, int limit) {
        String cleanTargetType = clean(targetType);
        String cleanTargetKey = clean(targetKey);
        if (cleanTargetType.isBlank() || cleanTargetKey.isBlank() || repository == null) {
            return List.of();
        }
        int normalizedLimit = normalizeLimit(limit);
        try {
            return repository.findRecentByTarget(cleanTargetType, cleanTargetKey, normalizedLimit);
        } catch (RuntimeException exception) {
            log.warn("Agent trace access audit target query failed, targetType={}, targetKey={}, limit={}, error={}",
                    cleanTargetType, cleanTargetKey, normalizedLimit, rootMessage(exception));
            return List.of();
        }
    }

    public List<AgentTraceAccessAuditView> findRecentByActor(String actor, int limit) {
        String cleanActor = clean(actor);
        if (cleanActor.isBlank() || repository == null) {
            return List.of();
        }
        int normalizedLimit = normalizeLimit(limit);
        try {
            return repository.findRecentByActor(cleanActor, normalizedLimit);
        } catch (RuntimeException exception) {
            log.warn("Agent trace access audit actor query failed, actor={}, limit={}, error={}",
                    cleanActor, normalizedLimit, rootMessage(exception));
            return List.of();
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
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
