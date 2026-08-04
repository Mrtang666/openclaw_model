package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunPhaseClassifierTests {

    private final AgentRunPhaseClassifier classifier = new AgentRunPhaseClassifier();

    @Test
    void classifiesStepTypesIntoOperationalPhases() {
        assertThat(classifier.classify(AgentRunStepType.MODEL_ROUND)).isEqualTo(AgentRunStepPhase.MODEL);
        assertThat(classifier.classify(AgentRunStepType.TOOL_CALL)).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(classifier.classify(AgentRunStepType.TOOL_RESULT)).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(classifier.classify(AgentRunStepType.POLICY_DECISION)).isEqualTo(AgentRunStepPhase.POLICY);
        assertThat(classifier.classify(AgentRunStepType.STOP)).isEqualTo(AgentRunStepPhase.TERMINAL);
    }

    @Test
    void groupsOnlyContiguousStepsWithSamePhase() {
        List<AgentRunDiagnosticPhaseView> phases = classifier.phases(List.of(
                step(1, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS),
                step(2, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.SUCCESS),
                step(3, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.SUCCESS),
                step(4, AgentRunStepType.POLICY_DECISION, AgentRunStepStatus.SKIPPED),
                step(5, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS)));

        assertThat(phases).extracting(AgentRunDiagnosticPhaseView::phase)
                .containsExactly(
                        AgentRunStepPhase.MODEL,
                        AgentRunStepPhase.TOOL,
                        AgentRunStepPhase.POLICY,
                        AgentRunStepPhase.MODEL);
        assertThat(phases.get(1).startStepIndex()).isEqualTo(2);
        assertThat(phases.get(1).endStepIndex()).isEqualTo(3);
        assertThat(phases.get(1).stepCount()).isEqualTo(2);
        assertThat(phases.get(2).status()).isEqualTo(AgentRunStepStatus.SKIPPED);
    }

    @Test
    void aggregatesPhaseStatusWithFailurePriority() {
        List<AgentRunDiagnosticPhaseView> phases = classifier.phases(List.of(
                step(1, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.SUCCESS),
                step(2, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.FAILED)));

        assertThat(phases).hasSize(1);
        assertThat(phases.get(0).phase()).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(phases.get(0).status()).isEqualTo(AgentRunStepStatus.FAILED);
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
