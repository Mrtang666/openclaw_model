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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class JdbcAgentTraceAccessAuditRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcAgentTraceAccessAuditRepository repository;

    @BeforeEach
    void clean() {
        assertUsingTestDatabase();
        jdbcTemplate.update("DELETE FROM agent_trace_access_audit");
        repository = new JdbcAgentTraceAccessAuditRepository(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void recordsTraceAccessAuditEvent() {
        repository.record(new AgentTraceAccessAuditEvent(
                "ops",
                "FIND_RUN",
                "RUN",
                "agent-run-1",
                true,
                "API_KEY_MATCHED",
                "127.0.0.1",
                "JUnit"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_trace_access_audit", Integer.class);
        String actor = jdbcTemplate.queryForObject(
                "SELECT actor FROM agent_trace_access_audit WHERE target_key = ?",
                String.class,
                "agent-run-1");
        Boolean allowed = jdbcTemplate.queryForObject(
                "SELECT allowed FROM agent_trace_access_audit WHERE target_key = ?",
                Boolean.class,
                "agent-run-1");
        Boolean createdAtPresent = jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM agent_trace_access_audit WHERE target_key = ?",
                Boolean.class,
                "agent-run-1");

        assertThat(count).isEqualTo(1);
        assertThat(actor).isEqualTo("ops");
        assertThat(allowed).isTrue();
        assertThat(createdAtPresent).isTrue();
    }

    private void assertUsingTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"openclaw_test".equals(database)) {
            throw new IllegalStateException("Trace audit tests must run against openclaw_test, current database: " + database);
        }
    }
}
