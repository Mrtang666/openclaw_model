package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStatsCalculatorTests {

    private final AgentRunStatsCalculator calculator = new AgentRunStatsCalculator(new AgentRunPhaseClassifier());

    @Test
    void calculatesOperationalStatsFromSteps() {
        AgentRunDiagnosticStatsView stats = calculator.stats(List.of(
                step(1, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS),
                step(2, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.STARTED),
                step(3, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.FAILED),
                step(4, AgentRunStepType.POLICY_DECISION, AgentRunStepStatus.SKIPPED),
                step(5, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS)));

        assertThat(stats.totalStepCount()).isEqualTo(5);
        assertThat(stats.modelRoundCount()).isEqualTo(2);
        assertThat(stats.toolCallCount()).isEqualTo(1);
        assertThat(stats.failedStepCount()).isEqualTo(1);
        assertThat(stats.skippedStepCount()).isEqualTo(1);
        assertThat(stats.phaseCount()).isEqualTo(4);
    }

    @Test
    void returnsZeroStatsForEmptySteps() {
        AgentRunDiagnosticStatsView stats = calculator.stats(List.of());

        assertThat(stats.totalStepCount()).isZero();
        assertThat(stats.modelRoundCount()).isZero();
        assertThat(stats.toolCallCount()).isZero();
        assertThat(stats.failedStepCount()).isZero();
        assertThat(stats.skippedStepCount()).isZero();
        assertThat(stats.phaseCount()).isZero();
    }

    private AgentRunStepView step(int index, AgentRunStepType type, AgentRunStepStatus status) {
        return new AgentRunStepView(
                index,
                index,
                type,
                1,
                "",
                status,
                "",
                "",
                "",
                Instant.parse("2026-08-03T06:00:00Z"));
    }
}
