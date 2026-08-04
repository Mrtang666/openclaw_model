package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs/access-audit")
public class AgentTraceAccessAuditController {

    private final AgentTraceDiagnosticAccessService accessService;
    private final AgentTraceAccessAuditQueryService auditQueryService;

    public AgentTraceAccessAuditController(
            AgentTraceDiagnosticAccessService accessService,
            AgentTraceAccessAuditQueryService auditQueryService) {
        this.accessService = accessService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ResponseEntity<List<AgentTraceAccessAuditView>> accessAudit(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetKey,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(name = "X-OpenClaw-Diagnostic-Key", required = false) String apiKey,
            @RequestHeader(name = "X-OpenClaw-Actor", required = false) String requestActor,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {
        AuditQueryTarget queryTarget = auditQueryTarget(targetType, targetKey, actor);
        AgentTraceAccessDecision decision = accessService.authorizeAndAudit(
                requestActor,
                apiKey,
                "FIND_ACCESS_AUDIT",
                queryTarget.auditTargetType(),
                queryTarget.auditTargetKey(),
                request,
                userAgent);
        if (!decision.allowed()) {
            return ResponseEntity.status(403)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        if (!queryTarget.valid()) {
            return ResponseEntity.badRequest()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        List<AgentTraceAccessAuditView> events = queryTarget.byTarget()
                ? auditQueryService.findRecentByTarget(clean(targetType), clean(targetKey), limit)
                : auditQueryService.findRecentByActor(clean(actor), limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    private AuditQueryTarget auditQueryTarget(String targetType, String targetKey, String actor) {
        boolean hasTargetType = !clean(targetType).isBlank();
        boolean hasTargetKey = !clean(targetKey).isBlank();
        boolean hasActor = !clean(actor).isBlank();
        if (hasTargetType && hasTargetKey) {
            return new AuditQueryTarget(true, true, "AUDIT_TARGET", clean(targetType) + ":" + clean(targetKey));
        }
        if (hasTargetType || hasTargetKey) {
            return new AuditQueryTarget(false, false, "AUDIT_QUERY", "INVALID_TARGET");
        }
        if (hasActor) {
            return new AuditQueryTarget(true, false, "AUDIT_ACTOR", clean(actor));
        }
        return new AuditQueryTarget(false, false, "AUDIT_QUERY", "MISSING_QUERY");
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private record AuditQueryTarget(boolean valid, boolean byTarget, String auditTargetType, String auditTargetKey) {
    }
}
