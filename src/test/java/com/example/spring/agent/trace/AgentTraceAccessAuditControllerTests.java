package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTraceAccessAuditController.class)
class AgentTraceAccessAuditControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentTraceDiagnosticAccessService accessService;

    @MockBean
    private AgentTraceAccessAuditQueryService auditQueryService;

    @Test
    void findsAccessAuditByTarget() throws Exception {
        allowAccess();
        when(auditQueryService.findRecentByTarget("RUN", "agent-run-1", 5))
                .thenReturn(List.of(auditView("ops", "FIND_RUN", "RUN", "agent-run-1", true)));

        mockMvc.perform(get("/api/agent-runs/access-audit")
                        .param("targetType", "RUN")
                        .param("targetKey", "agent-run-1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].actor").value("ops"))
                .andExpect(jsonPath("$[0].action").value("FIND_RUN"))
                .andExpect(jsonPath("$[0].targetType").value("RUN"))
                .andExpect(jsonPath("$[0].targetKey").value("agent-run-1"))
                .andExpect(jsonPath("$[0].allowed").value(true));

        verify(accessService).authorizeAndAudit(
                eq(null),
                eq(null),
                eq("FIND_ACCESS_AUDIT"),
                eq("AUDIT_TARGET"),
                eq("RUN:agent-run-1"),
                any(HttpServletRequest.class),
                eq(null));
        verify(auditQueryService).findRecentByTarget("RUN", "agent-run-1", 5);
    }

    @Test
    void findsAccessAuditByActor() throws Exception {
        allowAccess();
        when(auditQueryService.findRecentByActor("ops", 20))
                .thenReturn(List.of(auditView("ops", "FIND_RECENT_RUNS", "SESSION", "session-a", true)));

        mockMvc.perform(get("/api/agent-runs/access-audit").param("actor", "ops"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$[0].actor").value("ops"))
                .andExpect(jsonPath("$[0].targetType").value("SESSION"))
                .andExpect(jsonPath("$[0].targetKey").value("session-a"));

        verify(accessService).authorizeAndAudit(
                eq(null),
                eq(null),
                eq("FIND_ACCESS_AUDIT"),
                eq("AUDIT_ACTOR"),
                eq("ops"),
                any(HttpServletRequest.class),
                eq(null));
        verify(auditQueryService).findRecentByActor("ops", 20);
    }

    @Test
    void returnsBadRequestWhenAccessAuditQueryIsIncomplete() throws Exception {
        allowAccess();

        mockMvc.perform(get("/api/agent-runs/access-audit").param("targetType", "RUN"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(accessService).authorizeAndAudit(
                eq(null),
                eq(null),
                eq("FIND_ACCESS_AUDIT"),
                eq("AUDIT_QUERY"),
                eq("INVALID_TARGET"),
                any(HttpServletRequest.class),
                eq(null));
        verify(auditQueryService, never()).findRecentByTarget(anyString(), anyString(), anyInt());
        verify(auditQueryService, never()).findRecentByActor(anyString(), anyInt());
    }

    @Test
    void returnsForbiddenForAccessAuditWhenAccessDenied() throws Exception {
        when(accessService.authorizeAndAudit(
                nullable(String.class),
                nullable(String.class),
                eq("FIND_ACCESS_AUDIT"),
                eq("AUDIT_ACTOR"),
                eq("ops"),
                any(HttpServletRequest.class),
                nullable(String.class)))
                .thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISMATCH"));

        mockMvc.perform(get("/api/agent-runs/access-audit")
                        .param("actor", "ops")
                        .header("X-OpenClaw-Diagnostic-Key", "bad-key")
                        .header("X-OpenClaw-Actor", "security"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(accessService).authorizeAndAudit(
                eq("security"),
                eq("bad-key"),
                eq("FIND_ACCESS_AUDIT"),
                eq("AUDIT_ACTOR"),
                eq("ops"),
                any(HttpServletRequest.class),
                eq(null));
        verify(auditQueryService, never()).findRecentByActor(anyString(), anyInt());
    }

    private void allowAccess() {
        when(accessService.authorizeAndAudit(
                nullable(String.class),
                nullable(String.class),
                eq("FIND_ACCESS_AUDIT"),
                anyString(),
                anyString(),
                any(HttpServletRequest.class),
                nullable(String.class)))
                .thenReturn(new AgentTraceAccessDecision(true, "API_KEY_NOT_CONFIGURED"));
    }

    private AgentTraceAccessAuditView auditView(
            String actor,
            String action,
            String targetType,
            String targetKey,
            boolean allowed) {
        return new AgentTraceAccessAuditView(
                99L,
                actor,
                action,
                targetType,
                targetKey,
                allowed,
                allowed ? "API_KEY_MATCHED" : "API_KEY_MISMATCH",
                "127.0.0.1",
                "JUnit",
                Instant.parse("2026-08-03T06:00:00Z"));
    }
}
