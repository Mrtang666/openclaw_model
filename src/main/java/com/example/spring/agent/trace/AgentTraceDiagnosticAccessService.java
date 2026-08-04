package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceDiagnosticAccessService {

    private final AgentTraceAccessPolicy accessPolicy;
    private final AgentTraceAccessAuditService auditService;

    public AgentTraceDiagnosticAccessService(
            AgentTraceAccessPolicy accessPolicy,
            AgentTraceAccessAuditService auditService) {
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
    }

    public AgentTraceAccessDecision authorizeAndAudit(
            String actor,
            String apiKey,
            String action,
            String targetType,
            String targetKey,
            HttpServletRequest request,
            String userAgent) {
        AgentTraceAccessDecision decision = accessPolicy.authorize(clean(apiKey));
        auditService.record(new AgentTraceAccessAuditEvent(
                actorName(actor),
                action,
                targetType,
                targetKey,
                decision.allowed(),
                decision.reason(),
                request == null ? "" : request.getRemoteAddr(),
                userAgent));
        return decision;
    }

    private String actorName(String actor) {
        String cleanActor = clean(actor);
        return cleanActor.isBlank() ? "anonymous" : cleanActor;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
