package com.example.spring.agent.interrupts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInterruptServiceTests {

    @Test
    void acceptsInterruptOnlyForActiveRun() {
        AgentInterruptService service = new AgentInterruptService();

        assertThat(service.requestInterrupt("session-1", "取消")).isFalse();

        service.markRunStarted("session-1");

        assertThat(service.requestInterrupt("session-1", "取消")).isTrue();
        assertThat(service.isInterrupted("session-1")).isTrue();

        service.markRunFinished("session-1");

        assertThat(service.isInterrupted("session-1")).isFalse();
    }

    @Test
    void detectsCancelIntent() {
        AgentInterruptService service = new AgentInterruptService();

        assertThat(service.looksLikeInterrupt("取消")).isTrue();
        assertThat(service.looksLikeInterrupt("停止执行")).isTrue();
        assertThat(service.looksLikeInterrupt("别发邮件了")).isTrue();
        assertThat(service.looksLikeInterrupt("继续处理")).isFalse();
    }
}
