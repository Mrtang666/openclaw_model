package com.example.spring.agent.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class JdbcAgentRunQueryRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcAgentRunRepository writer;
    private JdbcAgentRunQueryRepository reader;

    @BeforeEach
    void clean() {
        assertUsingTestDatabase();
        jdbcTemplate.update("DELETE FROM agent_run_steps");
        jdbcTemplate.update("DELETE FROM agent_runs");
        writer = new JdbcAgentRunRepository(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC));
        reader = new JdbcAgentRunQueryRepository(jdbcTemplate);
    }

    @Test
    void findsRunWithOrderedStepsByRunKey() {
        AgentRunHandle handle = writer.createRun("WECHAT", "session-a", "hello", "context");
        writer.appendStep(
                handle,
                AgentRunStepType.TOOL_CALL,
                AgentRunStepStatus.STARTED,
                1,
                "weather",
                "city=hz",
                "",
                Map.of());
        writer.appendStep(
                handle,
                AgentRunStepType.TOOL_RESULT,
                AgentRunStepStatus.SUCCESS,
                1,
                "weather",
                "city=hz",
                "sunny",
                Map.of("source", "tool"));
        writer.completeRun(handle, AgentRunStatus.SUCCEEDED, "FINAL_ANSWER", "ok");

        Optional<AgentRunTraceView> result = reader.findRun(handle.runKey());

        assertThat(result).isPresent();
        AgentRunTraceView trace = result.get();
        assertThat(trace.runId()).isEqualTo(handle.runId());
        assertThat(trace.runKey()).isEqualTo(handle.runKey());
        assertThat(trace.channel()).isEqualTo("WECHAT");
        assertThat(trace.sessionKey()).isEqualTo("session-a");
        assertThat(trace.userText()).isEqualTo("hello");
        assertThat(trace.contextSummary()).isEqualTo("context");
        assertThat(trace.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(trace.stopReason()).isEqualTo("FINAL_ANSWER");
        assertThat(trace.finalReplySummary()).isEqualTo("ok");
        assertThat(trace.startedAt()).isEqualTo(Instant.parse("2026-08-03T06:00:00Z"));
        assertThat(trace.completedAt()).isEqualTo(Instant.parse("2026-08-03T06:00:00Z"));
        assertThat(trace.steps()).extracting(AgentRunStepView::stepIndex).containsExactly(1, 2);
        assertThat(trace.steps()).extracting(AgentRunStepView::stepType)
                .containsExactly(AgentRunStepType.TOOL_CALL, AgentRunStepType.TOOL_RESULT);
        assertThat(trace.steps().get(1).metadataJson())
                .contains("\"source\"")
                .contains("\"tool\"");
    }

    @Test
    void returnsEmptyWhenRunKeyDoesNotExist() {
        assertThat(reader.findRun("missing-run")).isEmpty();
    }

    @Test
    void findsRecentRunsBySessionKeyNewestFirst() throws Exception {
        AgentRunHandle first = writer.createRun("WECHAT", "session-b", "first", "context-1");
        Thread.sleep(5);
        AgentRunHandle second = writer.createRun("WECHAT", "session-b", "second", "context-2");
        Thread.sleep(5);
        writer.createRun("WECHAT", "other-session", "other", "context-3");
        writer.appendStep(
                second,
                AgentRunStepType.MODEL_ROUND,
                AgentRunStepStatus.SUCCESS,
                1,
                "",
                "messages=2",
                "tool_calls=[weather]",
                Map.of("tool_count", 1));
        writer.appendStep(
                second,
                AgentRunStepType.TOOL_CALL,
                AgentRunStepStatus.STARTED,
                1,
                "weather",
                "city=hz",
                "",
                Map.of());
        writer.appendStep(
                second,
                AgentRunStepType.TOOL_RESULT,
                AgentRunStepStatus.FAILED,
                1,
                "weather",
                "city=hz",
                "db down",
                Map.of());
        writer.completeRun(first, AgentRunStatus.SUCCEEDED, "FINAL_ANSWER", "first-ok");
        writer.completeRun(second, AgentRunStatus.FAILED, "TOOL_FAILURE", "second-failed");

        List<AgentRunSummaryView> summaries = reader.findRecentRuns("session-b", 1);

        assertThat(summaries).hasSize(1);
        AgentRunSummaryView summary = summaries.get(0);
        assertThat(summary.runKey()).isEqualTo(second.runKey());
        assertThat(summary.sessionKey()).isEqualTo("session-b");
        assertThat(summary.userText()).isEqualTo("second");
        assertThat(summary.contextSummary()).isEqualTo("context-2");
        assertThat(summary.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(summary.stopReason()).isEqualTo("TOOL_FAILURE");
        assertThat(summary.finalReplySummary()).isEqualTo("second-failed");
        assertThat(summary.stats().totalStepCount()).isEqualTo(3);
        assertThat(summary.stats().modelRoundCount()).isEqualTo(1);
        assertThat(summary.stats().toolCallCount()).isEqualTo(1);
        assertThat(summary.stats().failedStepCount()).isEqualTo(1);
        assertThat(summary.stats().skippedStepCount()).isZero();
        assertThat(summary.stats().phaseCount()).isEqualTo(2);
    }

    private void assertUsingTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"openclaw_test".equals(database)) {
            throw new IllegalStateException("Trace query tests must run against openclaw_test, current database: " + database);
        }
    }
}
