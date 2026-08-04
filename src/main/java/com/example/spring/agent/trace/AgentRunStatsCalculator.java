package com.example.spring.agent.trace;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentRunStatsCalculator {

    private final AgentRunPhaseClassifier phaseClassifier;

    public AgentRunStatsCalculator() {
        this(new AgentRunPhaseClassifier());
    }

    public AgentRunStatsCalculator(AgentRunPhaseClassifier phaseClassifier) {
        this.phaseClassifier = phaseClassifier == null ? new AgentRunPhaseClassifier() : phaseClassifier;
    }

    public AgentRunDiagnosticStatsView stats(List<AgentRunStepView> steps) {
        if (steps == null || steps.isEmpty()) {
            return AgentRunDiagnosticStatsView.empty();
        }

        int totalStepCount = 0;
        int modelRoundCount = 0;
        int toolCallCount = 0;
        int failedStepCount = 0;
        int skippedStepCount = 0;

        for (AgentRunStepView step : steps) {
            if (step == null) {
                continue;
            }
            totalStepCount++;
            if (step.stepType() == AgentRunStepType.MODEL_ROUND) {
                modelRoundCount++;
            }
            if (step.stepType() == AgentRunStepType.TOOL_CALL) {
                toolCallCount++;
            }
            if (step.status() == AgentRunStepStatus.FAILED) {
                failedStepCount++;
            }
            if (step.status() == AgentRunStepStatus.SKIPPED) {
                skippedStepCount++;
            }
        }

        return new AgentRunDiagnosticStatsView(
                totalStepCount,
                modelRoundCount,
                toolCallCount,
                failedStepCount,
                skippedStepCount,
                phaseClassifier.phases(steps).size());
    }
}
