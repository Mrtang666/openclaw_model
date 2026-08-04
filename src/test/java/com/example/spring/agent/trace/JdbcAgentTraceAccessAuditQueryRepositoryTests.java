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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class JdbcAgentTraceAccessAuditQueryRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcAgentTraceAccessAuditRepository writer;
    private JdbcAgentTraceAccessAuditQueryRepository reader;

    @BeforeEach
    void clean() {
        assertUsingTestDatabase();
        jdbcTemplate.update("DELETE FROM agent_trace_access_audit");
        writer = new JdbcAgentTraceAccessAuditRepository(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC));
        reader = new JdbcAgentTraceAccessAuditQueryRepository(jdbcTemplate);
    }

    @Test
    void findsRecentAuditEventsByTargetNewestFirst() throws Exception {
        writer.record(event("ops-a", "FIND_RUN", "RUN", "agent-run-1", true));
        Thread.sleep(5);
        writer.record(event("ops-b", "FIND_RUN", "RUN", "agent-run-1", false));
        Thread.sleep(5);
        writer.record(event("ops-c", "FIND_RUN", "RUN", "agent-run-2", true));

        List<AgentTraceAccessAuditView> events = reader.findRecentByTarget("RUN", "agent-run-1", 1);

        assertThat(events).hasSize(1);
        AgentTraceAccessAuditView event = events.get(0);
        assertThat(event.actor()).isEqualTo("ops-b");
        assertThat(event.action()).isEqualTo("FIND_RUN");
        assertThat(event.targetType()).isEqualTo("RUN");
        assertThat(event.targetKey()).isEqualTo("agent-run-1");
        assertThat(event.allowed()).isFalse();
        assertThat(event.createdAt()).isNotNull();
    }

    @Test
    void findsRecentAuditEventsByActorNewestFirst() throws Exception {
        writer.record(event("ops-a", "FIND_RUN", "RUN", "agent-run-1", true));
        Thread.sleep(5);
        writer.record(event("ops-a", "FIND_RECENT_RUNS", "SESSION", "session-1", true));
        Thread.sleep(5);
        writer.record(event("ops-b", "FIND_RUN", "RUN", "agent-run-2", true));

        List<AgentTraceAccessAuditView> events = reader.findRecentByActor("ops-a", 2);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(AgentTraceAccessAuditView::targetKey)
                .containsExactly("session-1", "agent-run-1");
    }

    private AgentTraceAccessAuditEvent event(
            String actor,
            String action,
            String targetType,
            String targetKey,
            boolean allowed) {
        return new AgentTraceAccessAuditEvent(
                actor,
                action,
                targetType,
                targetKey,
                allowed,
                allowed ? "API_KEY_MATCHED" : "API_KEY_MISMATCH",
                "127.0.0.1",
                "JUnit");
    }

    private void assertUsingTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"openclaw_test".equals(database)) {
            throw new IllegalStateException("Trace audit query tests must run against openclaw_test, current database: " + database);
        }
    }
}
