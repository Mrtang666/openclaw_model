package com.example.spring.agent.trace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Repository
public class JdbcAgentTraceAccessAuditRepository implements AgentTraceAccessAuditRepository {

    private static final int MAX_ACTOR_LENGTH = 191;
    private static final int MAX_ACTION_LENGTH = 64;
    private static final int MAX_TARGET_TYPE_LENGTH = 32;
    private static final int MAX_TARGET_KEY_LENGTH = 191;
    private static final int MAX_REASON_LENGTH = 64;
    private static final int MAX_REMOTE_ADDRESS_LENGTH = 128;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public JdbcAgentTraceAccessAuditRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    JdbcAgentTraceAccessAuditRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void record(AgentTraceAccessAuditEvent event) {
        if (event == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO agent_trace_access_audit
                        (actor, action, target_type, target_key, allowed, reason,
                         remote_address, user_agent, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                truncate(event.actor(), MAX_ACTOR_LENGTH),
                truncate(event.action(), MAX_ACTION_LENGTH),
                truncate(event.targetType(), MAX_TARGET_TYPE_LENGTH),
                truncate(event.targetKey(), MAX_TARGET_KEY_LENGTH),
                event.allowed(),
                truncate(event.reason(), MAX_REASON_LENGTH),
                blankToNull(truncate(event.remoteAddress(), MAX_REMOTE_ADDRESS_LENGTH)),
                blankToNull(truncate(event.userAgent(), MAX_USER_AGENT_LENGTH)),
                Timestamp.from(Instant.now(clock)));
    }

    private String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        String text = value == null ? "" : value.strip();
        return text.isBlank() ? null : text;
    }
}
