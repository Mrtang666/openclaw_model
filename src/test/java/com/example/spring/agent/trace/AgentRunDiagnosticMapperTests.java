package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunDiagnosticMapperTests {

    @Test
    void mapsTraceToRedactedDiagnosticView() {
        AgentRunDiagnosticMapper mapper = new AgentRunDiagnosticMapper(new AgentTraceRedactionPolicy());

        AgentRunDiagnosticTraceView diagnostic = mapper.toDiagnostic(traceView());

        assertThat(diagnostic.userText()).contains("a***@example.com");
        assertThat(diagnostic.userText()).contains("138****5678");
        assertThat(diagnostic.userText()).doesNotContain("alice@example.com", "13812345678");
        assertThat(diagnostic.contextSummary()).contains("token=[REDACTED]");
        assertThat(diagnostic.finalReplySummary()).contains("password=[REDACTED]");
        assertThat(diagnostic.steps()).hasSize(1);
        AgentRunDiagnosticStepView step = diagnostic.steps().get(0);
        assertThat(step.stepPhase()).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(step.inputSummary()).contains("api_key=[REDACTED]");
        assertThat(step.outputSummary()).contains("b***@example.com");
        assertThat(step.metadataJson()).contains("\"secret\":\"[REDACTED]\"");
        assertThat(step.metadataJson()).doesNotContain("top-secret");
        assertThat(diagnostic.phases()).hasSize(1);
        assertThat(diagnostic.phases().get(0).phase()).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(diagnostic.phases().get(0).startStepIndex()).isEqualTo(1);
        assertThat(diagnostic.phases().get(0).endStepIndex()).isEqualTo(1);
    }

    @Test
    void mapsSummaryToRedactedDiagnosticView() {
        AgentRunDiagnosticMapper mapper = new AgentRunDiagnosticMapper(new AgentTraceRedactionPolicy());

        AgentRunDiagnosticSummaryView diagnostic = mapper.toDiagnostic(summaryView());

        assertThat(diagnostic.userText()).contains("139****5678");
        assertThat(diagnostic.contextSummary()).contains("c***@example.com");
        assertThat(diagnostic.finalReplySummary()).contains("token=[REDACTED]");
        assertThat(diagnostic.userText()).doesNotContain("13912345678");
        assertThat(diagnostic.contextSummary()).doesNotContain("carol@example.com");
        assertThat(diagnostic.finalReplySummary()).doesNotContain("raw-token");
    }

    private AgentRunTraceView traceView() {
        return new AgentRunTraceView(
                1L,
                "agent-run-1",
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
                        AgentRunStepType.TOOL_RESULT,
                        2,
                        "web_search",
                        AgentRunStepStatus.SUCCESS,
                        "api_key=sk-live-123",
                        "mail bob@example.com",
                        "{\"secret\":\"top-secret\"}",
                        Instant.parse("2026-08-03T06:00:00Z"))));
    }

    private AgentRunSummaryView summaryView() {
        return new AgentRunSummaryView(
                2L,
                "agent-run-2",
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
}
