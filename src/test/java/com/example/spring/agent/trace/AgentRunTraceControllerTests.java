package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentRunTraceController.class)
@Import(AgentRunDiagnosticMapper.class)
class AgentRunTraceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRunTraceQueryService queryService;

    @MockBean
    private AgentTraceAccessPolicy accessPolicy;

    @MockBean
    private AgentTraceAccessAuditService auditService;

    @MockBean
    private AgentTraceAccessAuditQueryService auditQueryService;

    @Test
    void findsRunByRunKey() throws Exception {
        allowAccessWithoutConfiguredKey();
        when(queryService.findRun("agent-run-1")).thenReturn(Optional.of(traceView("agent-run-1")));

        mockMvc.perform(get("/api/agent-runs/agent-run-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.runId").value(1))
                .andExpect(jsonPath("$.runKey").value("agent-run-1"))
                .andExpect(jsonPath("$.sessionKey").value("session-a"))
                .andExpect(jsonPath("$.userText").value("contact a***@example.com or 138****5678"))
                .andExpect(jsonPath("$.contextSummary").value("context token=[REDACTED]"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finalReplySummary").value("reply password=[REDACTED]"))
                .andExpect(jsonPath("$.steps[0].stepIndex").value(1))
                .andExpect(jsonPath("$.steps[0].stepType").value("POLICY_DECISION"))
                .andExpect(jsonPath("$.steps[0].inputSummary").value("api_key=[REDACTED]"))
                .andExpect(jsonPath("$.steps[0].outputSummary").value("mail b***@example.com"))
                .andExpect(jsonPath("$.steps[0].metadataJson").value("{\"secret\":\"[REDACTED]\"}"))
                .andExpect(content().string(not(containsString("alice@example.com"))))
                .andExpect(content().string(not(containsString("13812345678"))))
                .andExpect(content().string(not(containsString("sk-live-123"))))
                .andExpect(content().string(not(containsString("top-secret"))));

        verify(accessPolicy).authorize("");
        verify(auditService).record(argThat(event ->
                "anonymous".equals(event.actor())
                        && "FIND_RUN".equals(event.action())
                        && "RUN".equals(event.targetType())
                        && "agent-run-1".equals(event.targetKey())
                        && event.allowed()
                        && "API_KEY_NOT_CONFIGURED".equals(event.reason())));
        verify(queryService).findRun("agent-run-1");
    }

    @Test
    void returnsNotFoundWhenRunDoesNotExist() throws Exception {
        allowAccessWithoutConfiguredKey();
        when(queryService.findRun("missing-run")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-runs/missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(auditService).record(argThat(event ->
                "FIND_RUN".equals(event.action())
                        && "RUN".equals(event.targetType())
                        && "missing-run".equals(event.targetKey())
                        && event.allowed()));
        verify(queryService).findRun("missing-run");
    }

    @Test
    void findsRecentRunsBySessionKey() throws Exception {
        allowAccessWithoutConfiguredKey();
        when(queryService.findRecentRuns("session-a", 5))
                .thenReturn(List.of(summaryView("agent-run-2")));

        mockMvc.perform(get("/api/agent-runs")
                        .param("sessionKey", "session-a")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$[0].runId").value(2))
                .andExpect(jsonPath("$[0].runKey").value("agent-run-2"))
                .andExpect(jsonPath("$[0].sessionKey").value("session-a"))
                .andExpect(jsonPath("$[0].userText").value("phone 139****5678"))
                .andExpect(jsonPath("$[0].contextSummary").value("email c***@example.com"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].stopReason").value("TOOL_FAILURE"))
                .andExpect(jsonPath("$[0].finalReplySummary").value("token=[REDACTED]"))
                .andExpect(content().string(not(containsString("13912345678"))))
                .andExpect(content().string(not(containsString("carol@example.com"))))
                .andExpect(content().string(not(containsString("raw-token"))));

        verify(accessPolicy).authorize("");
        verify(auditService).record(argThat(event ->
                "FIND_RECENT_RUNS".equals(event.action())
                        && "SESSION".equals(event.targetType())
                        && "session-a".equals(event.targetKey())
                        && event.allowed()));
        verify(queryService).findRecentRuns("session-a", 5);
    }

    @Test
    void usesDefaultLimitForRecentRuns() throws Exception {
        allowAccessWithoutConfiguredKey();
        mockMvc.perform(get("/api/agent-runs").param("sessionKey", "session-a"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(queryService).findRecentRuns("session-a", 20);
    }

    @Test
    void returnsForbiddenAndAuditsWhenAccessDenied() throws Exception {
        when(accessPolicy.authorize("bad-key"))
                .thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISMATCH"));

        mockMvc.perform(get("/api/agent-runs/agent-run-1")
                        .header("X-OpenClaw-Diagnostic-Key", "bad-key")
                        .header("X-OpenClaw-Actor", "ops")
                        .header(HttpHeaders.USER_AGENT, "JUnit"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(auditService).record(argThat(event ->
                "ops".equals(event.actor())
                        && "FIND_RUN".equals(event.action())
                        && "RUN".equals(event.targetType())
                        && "agent-run-1".equals(event.targetKey())
                        && !event.allowed()
                        && "API_KEY_MISMATCH".equals(event.reason())
                        && "JUnit".equals(event.userAgent())));
        verify(queryService, never()).findRun(anyString());
    }

    @Test
    void findsAccessAuditByTarget() throws Exception {
        allowAccessWithoutConfiguredKey();
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

        verify(auditService).record(argThat(event ->
                "FIND_ACCESS_AUDIT".equals(event.action())
                        && "AUDIT_TARGET".equals(event.targetType())
                        && "RUN:agent-run-1".equals(event.targetKey())
                        && event.allowed()));
        verify(auditQueryService).findRecentByTarget("RUN", "agent-run-1", 5);
    }

    @Test
    void findsAccessAuditByActor() throws Exception {
        allowAccessWithoutConfiguredKey();
        when(auditQueryService.findRecentByActor("ops", 20))
                .thenReturn(List.of(auditView("ops", "FIND_RECENT_RUNS", "SESSION", "session-a", true)));

        mockMvc.perform(get("/api/agent-runs/access-audit").param("actor", "ops"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$[0].actor").value("ops"))
                .andExpect(jsonPath("$[0].targetType").value("SESSION"))
                .andExpect(jsonPath("$[0].targetKey").value("session-a"));

        verify(auditService).record(argThat(event ->
                "FIND_ACCESS_AUDIT".equals(event.action())
                        && "AUDIT_ACTOR".equals(event.targetType())
                        && "ops".equals(event.targetKey())
                        && event.allowed()));
        verify(auditQueryService).findRecentByActor("ops", 20);
    }

    @Test
    void returnsBadRequestWhenAccessAuditQueryIsIncomplete() throws Exception {
        allowAccessWithoutConfiguredKey();

        mockMvc.perform(get("/api/agent-runs/access-audit").param("targetType", "RUN"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void returnsForbiddenForAccessAuditWhenAccessDenied() throws Exception {
        when(accessPolicy.authorize("bad-key"))
                .thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISMATCH"));

        mockMvc.perform(get("/api/agent-runs/access-audit")
                        .param("actor", "ops")
                        .header("X-OpenClaw-Diagnostic-Key", "bad-key")
                        .header("X-OpenClaw-Actor", "security"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(auditService).record(argThat(event ->
                "security".equals(event.actor())
                        && "FIND_ACCESS_AUDIT".equals(event.action())
                        && "AUDIT_ACTOR".equals(event.targetType())
                        && "ops".equals(event.targetKey())
                        && !event.allowed()
                        && "API_KEY_MISMATCH".equals(event.reason())));
        verify(auditQueryService, never()).findRecentByActor(anyString(), anyInt());
    }

    private void allowAccessWithoutConfiguredKey() {
        when(accessPolicy.authorize(""))
                .thenReturn(new AgentTraceAccessDecision(true, "API_KEY_NOT_CONFIGURED"));
    }

    private AgentRunTraceView traceView(String runKey) {
        return new AgentRunTraceView(
                1L,
                runKey,
                "WECHAT",
                "session-a",
                "contact alice@example.com or 13812345678",
                "context token=abc123",
                AgentRunStatus.SUCCEEDED,
                "FINAL_ANSWER",
                "reply password=p@ss",
                Instant.parse("2026-08-03T06:00:00Z"),
                Instant.parse("2026-08-03T06:00:01Z"),
                List.of(new AgentRunStepView(
                        11L,
                        1,
                        AgentRunStepType.POLICY_DECISION,
                        2,
                        "web_search",
                        AgentRunStepStatus.SKIPPED,
                        "api_key=sk-live-123",
                        "mail bob@example.com",
                        "{\"secret\":\"top-secret\"}",
                        Instant.parse("2026-08-03T06:00:00Z"))));
    }

    private AgentRunSummaryView summaryView(String runKey) {
        return new AgentRunSummaryView(
                2L,
                runKey,
                "WECHAT",
                "session-a",
                "phone 13912345678",
                "email carol@example.com",
                AgentRunStatus.FAILED,
                "TOOL_FAILURE",
                "token=raw-token",
                Instant.parse("2026-08-03T06:00:00Z"),
                Instant.parse("2026-08-03T06:00:01Z"));
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
