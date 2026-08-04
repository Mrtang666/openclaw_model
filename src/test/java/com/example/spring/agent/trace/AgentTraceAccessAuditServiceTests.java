package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTraceAccessAuditServiceTests {

    @Test
    void delegatesAuditEventToRepository() {
        AgentTraceAccessAuditRepository repository = mock(AgentTraceAccessAuditRepository.class);
        AgentTraceAccessAuditService service = new AgentTraceAccessAuditService(repository);
        AgentTraceAccessAuditEvent event = event();

        service.record(event);

        verify(repository).record(event);
    }

    @Test
    void swallowsRepositoryFailures() {
        AgentTraceAccessAuditRepository repository = mock(AgentTraceAccessAuditRepository.class);
        AgentTraceAccessAuditEvent event = event();
        doThrow(new IllegalStateException("db down")).when(repository).record(event);
        AgentTraceAccessAuditService service = new AgentTraceAccessAuditService(repository);

        assertThatCode(() -> service.record(event)).doesNotThrowAnyException();
    }

    private AgentTraceAccessAuditEvent event() {
        return new AgentTraceAccessAuditEvent(
                "ops",
                "FIND_RUN",
                "RUN",
                "agent-run-1",
                true,
                "API_KEY_MATCHED",
                "127.0.0.1",
                "JUnit");
    }
}
