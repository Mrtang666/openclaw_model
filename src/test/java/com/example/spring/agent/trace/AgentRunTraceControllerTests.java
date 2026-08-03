package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentRunTraceController.class)
class AgentRunTraceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRunTraceQueryService queryService;

    @Test
    void findsRunByRunKey() throws Exception {
        when(queryService.findRun("agent-run-1")).thenReturn(Optional.of(traceView("agent-run-1")));

        mockMvc.perform(get("/api/agent-runs/agent-run-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.runId").value(1))
                .andExpect(jsonPath("$.runKey").value("agent-run-1"))
                .andExpect(jsonPath("$.sessionKey").value("session-a"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[0].stepIndex").value(1))
                .andExpect(jsonPath("$.steps[0].stepType").value("POLICY_DECISION"));

        verify(queryService).findRun("agent-run-1");
    }

    @Test
    void returnsNotFoundWhenRunDoesNotExist() throws Exception {
        when(queryService.findRun("missing-run")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/agent-runs/missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(queryService).findRun("missing-run");
    }

    @Test
    void findsRecentRunsBySessionKey() throws Exception {
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
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].stopReason").value("TOOL_FAILURE"));

        verify(queryService).findRecentRuns("session-a", 5);
    }

    @Test
    void usesDefaultLimitForRecentRuns() throws Exception {
        mockMvc.perform(get("/api/agent-runs").param("sessionKey", "session-a"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        verify(queryService).findRecentRuns("session-a", 20);
    }

    private AgentRunTraceView traceView(String runKey) {
        return new AgentRunTraceView(
                1L,
                runKey,
                "WECHAT",
                "session-a",
                "hello",
                "context",
                AgentRunStatus.SUCCEEDED,
                "FINAL_ANSWER",
                "ok",
                Instant.parse("2026-08-03T06:00:00Z"),
                Instant.parse("2026-08-03T06:00:01Z"),
                List.of(new AgentRunStepView(
                        11L,
                        1,
                        AgentRunStepType.POLICY_DECISION,
                        2,
                        "web_search",
                        AgentRunStepStatus.SKIPPED,
                        "has rag evidence",
                        "skip web search",
                        "{\"decision_type\":\"SKIP_WEB_SEARCH_RAG_EVIDENCE\"}",
                        Instant.parse("2026-08-03T06:00:00Z"))));
    }

    private AgentRunSummaryView summaryView(String runKey) {
        return new AgentRunSummaryView(
                2L,
                runKey,
                "WECHAT",
                "session-a",
                "hello",
                "context",
                AgentRunStatus.FAILED,
                "TOOL_FAILURE",
                "failed",
                Instant.parse("2026-08-03T06:00:00Z"),
                Instant.parse("2026-08-03T06:00:01Z"));
    }
}
