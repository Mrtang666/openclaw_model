package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunTraceController {

    private final AgentRunTraceQueryService queryService;
    private final AgentRunDiagnosticMapper diagnosticMapper;
    private final AgentTraceDiagnosticAccessService accessService;

    public AgentRunTraceController(
            AgentRunTraceQueryService queryService,
            AgentRunDiagnosticMapper diagnosticMapper,
            AgentTraceDiagnosticAccessService accessService) {
        this.queryService = queryService;
        this.diagnosticMapper = diagnosticMapper;
        this.accessService = accessService;
    }

    @GetMapping("/{runKey}")
    public ResponseEntity<AgentRunDiagnosticTraceView> run(
            @PathVariable String runKey,
            @RequestHeader(name = "X-OpenClaw-Diagnostic-Key", required = false) String apiKey,
            @RequestHeader(name = "X-OpenClaw-Actor", required = false) String actor,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest request) {
        AgentTraceAccessDecision decision = accessService.authorizeAndAudit(
                actor,
                apiKey,
                "FIND_RUN",
                "RUN",
                runKey,
                request,
                userAgent);
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
        AgentTraceAccessDecision decision = accessService.authorizeAndAudit(
                actor,
                apiKey,
                "FIND_RECENT_RUNS",
                "SESSION",
                sessionKey,
                request,
                userAgent);
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
}
