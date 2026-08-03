package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentTraceAccessAuditQueryServiceTests {

    @Test
    void trimsTargetAndClampsLimitBeforeDelegating() {
        AgentTraceAccessAuditQueryRepository repository = mock(AgentTraceAccessAuditQueryRepository.class);
        AgentTraceAccessAuditView view = view();
        when(repository.findRecentByTarget("RUN", "agent-run-1", 100)).thenReturn(List.of(view));
        AgentTraceAccessAuditQueryService service = new AgentTraceAccessAuditQueryService(repository);

        List<AgentTraceAccessAuditView> result = service.findRecentByTarget(" RUN ", " agent-run-1 ", 999);

        assertThat(result).containsExactly(view);
        verify(repository).findRecentByTarget("RUN", "agent-run-1", 100);
    }

    @Test
    void usesDefaultLimitForActorQueryWhenLimitIsNotPositive() {
        AgentTraceAccessAuditQueryRepository repository = mock(AgentTraceAccessAuditQueryRepository.class);
        AgentTraceAccessAuditQueryService service = new AgentTraceAccessAuditQueryService(repository);

        service.findRecentByActor("ops", 0);

        verify(repository).findRecentByActor("ops", 20);
    }

    @Test
    void returnsEmptyForBlankTargetWithoutCallingRepository() {
        AgentTraceAccessAuditQueryRepository repository = mock(AgentTraceAccessAuditQueryRepository.class);
        AgentTraceAccessAuditQueryService service = new AgentTraceAccessAuditQueryService(repository);

        assertThat(service.findRecentByTarget("RUN", " ", 20)).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void swallowsRepositoryFailures() {
        AgentTraceAccessAuditQueryRepository repository = mock(AgentTraceAccessAuditQueryRepository.class);
        doThrow(new IllegalStateException("db down"))
                .when(repository).findRecentByActor("ops", 20);
        AgentTraceAccessAuditQueryService service = new AgentTraceAccessAuditQueryService(repository);

        assertThat(service.findRecentByActor("ops", 20)).isEmpty();
    }

    private AgentTraceAccessAuditView view() {
        return new AgentTraceAccessAuditView(
                1L,
                "ops",
                "FIND_RUN",
                "RUN",
                "agent-run-1",
                true,
                "API_KEY_MATCHED",
                "127.0.0.1",
                "JUnit",
                Instant.parse("2026-08-03T06:00:00Z"));
    }
}
