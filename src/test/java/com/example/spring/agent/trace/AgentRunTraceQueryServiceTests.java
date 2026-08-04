package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentRunTraceQueryServiceTests {

    @Test
    void trimsRunKeyBeforeDelegating() {
        AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
        AgentRunTraceView view = traceView("agent-run-1");
        when(repository.findRun("agent-run-1")).thenReturn(Optional.of(view));
        AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

        Optional<AgentRunTraceView> result = service.findRun(" agent-run-1 ");

        assertThat(result).contains(view);
        verify(repository).findRun("agent-run-1");
    }

    @Test
    void returnsEmptyForBlankRunKeyWithoutCallingRepository() {
        AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
        AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

        assertThat(service.findRun("   ")).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void clampsRecentRunsLimitAndDelegatesToRepository() {
        AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
        AgentRunSummaryView summary = summaryView("agent-run-2");
        when(repository.findRecentRuns("session", 100)).thenReturn(List.of(summary));
        AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

        List<AgentRunSummaryView> result = service.findRecentRuns(" session ", 999);

        assertThat(result).containsExactly(summary);
        verify(repository).findRecentRuns("session", 100);
    }

    @Test
    void usesDefaultRecentRunsLimitWhenLimitIsNotPositive() {
        AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
        AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

        service.findRecentRuns("session", 0);

        verify(repository).findRecentRuns("session", 20);
    }

    @Test
    void returnsEmptyResultsWhenRepositoryFails() {
        AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
        doThrow(new IllegalStateException("db down")).when(repository).findRun("agent-run-3");
        doThrow(new IllegalStateException("db down")).when(repository).findRecentRuns("session", 20);
        AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

        assertThat(service.findRun("agent-run-3")).isEmpty();
        assertThat(service.findRecentRuns("session", 20)).isEmpty();
    }

    private AgentRunTraceView traceView(String runKey) {
        return new AgentRunTraceView(
                1L,
                runKey,
                "WECHAT",
                "session",
                "hello",
                "context",
                AgentRunStatus.SUCCEEDED,
                "FINAL_ANSWER",
                "ok",
                Instant.parse("2026-08-03T06:00:00Z"),
                Instant.parse("2026-08-03T06:00:01Z"),
                List.of());
    }

    private AgentRunSummaryView summaryView(String runKey) {
        return new AgentRunSummaryView(
                2L,
                runKey,
                "WECHAT",
                "session",
                "hello",
                "context",
                AgentRunStatus.RUNNING,
                "",
                "",
                Instant.parse("2026-08-03T06:00:00Z"),
                null,
                AgentRunDiagnosticStatsView.empty());
    }
}
