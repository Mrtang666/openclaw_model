package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBudgetManagerTests {

    @Test
    void calculatesContextBudgetAfterReservesAndRatio() {
        WechatContextProperties properties = new WechatContextProperties(
                true, true, true, 5, 1, 2, 5, 1,
                128_000, 8_000, 12_000, 0.8);
        ContextBudgetManager manager = new ContextBudgetManager(properties, new ConservativeTokenEstimator());

        ContextBudgetReport report = manager.report("x".repeat(1_000), false);

        assertThat(report.availableInputTokens()).isEqualTo(108_000);
        assertThat(report.contextBudgetTokens()).isEqualTo(86_400);
        assertThat(report.actualTokens()).isEqualTo(1_000);
        assertThat(report.compressed()).isFalse();
    }
}
