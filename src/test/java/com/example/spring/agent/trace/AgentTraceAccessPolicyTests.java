package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTraceAccessPolicyTests {

    @Test
    void allowsAccessWhenApiKeyIsNotConfigured() {
        AgentTraceAccessDecision decision = new AgentTraceAccessPolicy("").authorize("");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("API_KEY_NOT_CONFIGURED");
    }

    @Test
    void allowsAccessWhenConfiguredApiKeyMatches() {
        AgentTraceAccessDecision decision = new AgentTraceAccessPolicy("secret-key").authorize(" secret-key ");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("API_KEY_MATCHED");
    }

    @Test
    void deniesAccessWhenConfiguredApiKeyIsMissing() {
        AgentTraceAccessDecision decision = new AgentTraceAccessPolicy("secret-key").authorize("");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("API_KEY_MISSING");
    }

    @Test
    void deniesAccessWhenConfiguredApiKeyDoesNotMatch() {
        AgentTraceAccessDecision decision = new AgentTraceAccessPolicy("secret-key").authorize("bad-key");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("API_KEY_MISMATCH");
    }
}
