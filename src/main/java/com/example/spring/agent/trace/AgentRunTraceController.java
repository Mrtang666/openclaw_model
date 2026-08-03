package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunTraceController {

    private final AgentRunTraceQueryService queryService;
    private final AgentRunDiagnosticMapper diagnosticMapper;
    private final AgentTraceAccessPolicy accessPolicy;
    private final AgentTraceAccessAuditService auditService;
    private final AgentTraceAccessAuditQueryService auditQueryService;

    public AgentRunTraceController(
            AgentRunTraceQueryService queryService,
            AgentRunDiagnosticMapper diagnosticMapper,
            AgentTraceAccessPolicy accessPolicy,
            AgentTraceAccessAuditService auditService,
            AgentTraceAccessAuditQueryService auditQueryService) {
        this.queryService = queryService;
        this.diagnosticMapper = diagnosticMapper;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/access-audit")
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
        AgentTraceAccessDecision decision = accessPolicy.authorize(clean(apiKey));
        recordAudit(requestActor, "FIND_ACCESS_AUDIT", queryTarget.auditTargetType(), queryTarget.auditTargetKey(),
                decision, request, userAgent);
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

    @GetMapping("/{runKey}")
    public ResponseEntity<AgentRunDiagnosticTraceView> run(
            @PathVariable String runKey,
            @RequestHeader(name = "X-OpenClaw-Diagnostic-Key", required = false) String apiKey,
            @RequestHeader(name = "X-OpenClaw-Actor", required = false) String actor,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {
        AgentTraceAccessDecision decision = accessPolicy.authorize(clean(apiKey));
        recordAudit(actor, "FIND_RUN", "RUN", runKey, decision, request, userAgent);
        if (!decision.allowed()) {
            return ResponseEntity.status(403)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return queryService.findRun(runKey)
                .map(trace -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(diagnosticMapper.toDiagnostic(trace)))
                .orElseGet(() -> ResponseEntity.notFound()
                        .cacheControl(CacheControl.noStore())
                        .build());
    }

    @GetMapping
    public ResponseEntity<List<AgentRunDiagnosticSummaryView>> recentRuns(
            @RequestParam String sessionKey,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(name = "X-OpenClaw-Diagnostic-Key", required = false) String apiKey,
            @RequestHeader(name = "X-OpenClaw-Actor", required = false) String actor,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {
        AgentTraceAccessDecision decision = accessPolicy.authorize(clean(apiKey));
        recordAudit(actor, "FIND_RECENT_RUNS", "SESSION", sessionKey, decision, request, userAgent);
        if (!decision.allowed()) {
            return ResponseEntity.status(403)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.findRecentRuns(sessionKey, limit).stream()
                        .map(diagnosticMapper::toDiagnostic)
                        .toList());
    }

    private void recordAudit(
            String actor,
            String action,
            String targetType,
            String targetKey,
            AgentTraceAccessDecision decision,
            HttpServletRequest request,
            String userAgent) {
        auditService.record(new AgentTraceAccessAuditEvent(
                actor,
                action,
                targetType,
                targetKey,
                decision.allowed(),
                decision.reason(),
                request == null ? "" : request.getRemoteAddr(),
                userAgent));
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
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

    private record AuditQueryTarget(boolean valid, boolean byTarget, String auditTargetType, String auditTargetKey) {
    }
}
