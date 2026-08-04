package com.example.spring.agent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceAccessAuditService.class);

    private final AgentTraceAccessAuditRepository repository;

    public AgentTraceAccessAuditService(AgentTraceAccessAuditRepository repository) {
        this.repository = repository;
    }

    public void record(AgentTraceAccessAuditEvent event) {
        try {
            if (repository != null && event != null) {
                repository.record(event);
            }
        } catch (RuntimeException exception) {
            log.warn("Agent trace access audit failed, actor={}, action={}, targetType={}, targetKey={}, error={}",
                    event.actor(), event.action(), event.targetType(), event.targetKey(), rootMessage(exception));
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
