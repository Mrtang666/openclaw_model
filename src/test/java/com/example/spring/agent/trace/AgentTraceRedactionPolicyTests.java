package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTraceRedactionPolicyTests {

    @Test
    void redactsEmailPhoneAndSensitiveKeyValues() {
        AgentTraceRedactionPolicy policy = new AgentTraceRedactionPolicy();

        String redacted = policy.redact("email=alice@example.com phone=13812345678 token=abc123");

        assertThat(redacted).contains("a***@example.com");
        assertThat(redacted).contains("138****5678");
        assertThat(redacted).contains("token=[REDACTED]");
        assertThat(redacted).doesNotContain("alice@example.com", "abc123");
    }

    @Test
    void redactsJsonStyleSensitiveKeyValues() {
        AgentTraceRedactionPolicy policy = new AgentTraceRedactionPolicy();

        String redacted = policy.redact("{\"api_key\":\"sk-live-123\", \"password\": \"p@ssw0rd\"}");

        assertThat(redacted).contains("\"api_key\"");
        assertThat(redacted).contains("\"password\"");
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("sk-live-123", "p@ssw0rd");
    }

    @Test
    void truncatesLongDiagnosticText() {
        AgentTraceRedactionPolicy policy = new AgentTraceRedactionPolicy(16);

        String redacted = policy.redact("abcdefghijklmnopqrst");

        assertThat(redacted).isEqualTo("abcdefghijklmnop... [TRUNCATED]");
    }

    @Test
    void returnsBlankForNullInput() {
        AgentTraceRedactionPolicy policy = new AgentTraceRedactionPolicy();

        assertThat(policy.redact(null)).isEmpty();
    }
}
