package com.example.spring.agent.trace;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunTraceController {

    private final AgentRunTraceQueryService queryService;
    private final AgentRunDiagnosticMapper diagnosticMapper;

    public AgentRunTraceController(AgentRunTraceQueryService queryService, AgentRunDiagnosticMapper diagnosticMapper) {
        this.queryService = queryService;
        this.diagnosticMapper = diagnosticMapper;
    }

    @GetMapping("/{runKey}")
    public ResponseEntity<AgentRunDiagnosticTraceView> run(@PathVariable String runKey) {
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
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.findRecentRuns(sessionKey, limit).stream()
                        .map(diagnosticMapper::toDiagnostic)
                        .toList());
    }
}
