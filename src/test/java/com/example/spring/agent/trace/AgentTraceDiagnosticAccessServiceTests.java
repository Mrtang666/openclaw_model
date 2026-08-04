package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTraceDiagnosticAccessServiceTests {

    private final AgentTraceAccessPolicy accessPolicy = mock(AgentTraceAccessPolicy.class);
    private final AgentTraceAccessAuditService auditService = mock(AgentTraceAccessAuditService.class);
    private final AgentTraceDiagnosticAccessService service =
            new AgentTraceDiagnosticAccessService(accessPolicy, auditService);

    @Test
    void authorizesWithTrimmedApiKeyAndAuditsAccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.7");
        when(accessPolicy.authorize("secret"))
                .thenReturn(new AgentTraceAccessDecision(true, "API_KEY_MATCHED"));

        AgentTraceAccessDecision decision = service.authorizeAndAudit(
                " ops ",
                " secret ",
                "FIND_RUN",
                "RUN",
                "agent-run-1",
                request,
                "JUnit");

        assertThat(decision.allowed()).isTrue();
        verify(accessPolicy).authorize("secret");
        verify(auditService).record(argThat(event ->
                "ops".equals(event.actor())
                        && "FIND_RUN".equals(event.action())
                        && "RUN".equals(event.targetType())
                        && "agent-run-1".equals(event.targetKey())
                        && event.allowed()
                        && "API_KEY_MATCHED".equals(event.reason())
                        && "10.0.0.7".equals(event.remoteAddress())
                        && "JUnit".equals(event.userAgent())));
    }

    @Test
    void usesAnonymousActorWhenActorIsBlank() {
        when(accessPolicy.authorize(""))
                .thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISSING"));

        service.authorizeAndAudit(
                " ",
                null,
                "FIND_RECENT_RUNS",
                "SESSION",
                "session-a",
                null,
                null);

        verify(auditService).record(argThat(event ->
                "anonymous".equals(event.actor())
                        && !event.allowed()
                        && "API_KEY_MISSING".equals(event.reason())
                        && "".equals(event.remoteAddress())));
    }
}
