package com.example.spring.agent.trace;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentRunPhaseClassifier {

    public AgentRunStepPhase classify(AgentRunStepType stepType) {
        if (stepType == AgentRunStepType.TOOL_CALL || stepType == AgentRunStepType.TOOL_RESULT) {
            return AgentRunStepPhase.TOOL;
        }
        if (stepType == AgentRunStepType.POLICY_DECISION) {
            return AgentRunStepPhase.POLICY;
        }
        if (stepType == AgentRunStepType.STOP) {
            return AgentRunStepPhase.TERMINAL;
        }
        return AgentRunStepPhase.MODEL;
    }

    public List<AgentRunDiagnosticPhaseView> phases(List<AgentRunStepView> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<AgentRunDiagnosticPhaseView> phases = new ArrayList<>();
        List<AgentRunStepView> currentSteps = new ArrayList<>();
        AgentRunStepPhase currentPhase = null;

        for (AgentRunStepView step : steps) {
            AgentRunStepPhase stepPhase = classify(step == null ? null : step.stepType());
            if (currentPhase != null && currentPhase != stepPhase) {
                phases.add(toPhaseView(currentPhase, currentSteps));
                currentSteps = new ArrayList<>();
            }
            currentPhase = stepPhase;
            currentSteps.add(step);
        }

        if (!currentSteps.isEmpty()) {
            phases.add(toPhaseView(currentPhase, currentSteps));
        }
        return List.copyOf(phases);
    }

    private AgentRunDiagnosticPhaseView toPhaseView(AgentRunStepPhase phase, List<AgentRunStepView> steps) {
        return new AgentRunDiagnosticPhaseView(
                phase,
                firstStepIndex(steps),
                lastStepIndex(steps),
                steps.size(),
                aggregateStatus(steps));
    }

    private int firstStepIndex(List<AgentRunStepView> steps) {
        AgentRunStepView first = steps.get(0);
        return first == null ? 0 : first.stepIndex();
    }

    private int lastStepIndex(List<AgentRunStepView> steps) {
        AgentRunStepView last = steps.get(steps.size() - 1);
        return last == null ? 0 : last.stepIndex();
    }

    private AgentRunStepStatus aggregateStatus(List<AgentRunStepView> steps) {
        boolean hasStarted = false;
        boolean allSkipped = true;
        for (AgentRunStepView step : steps) {
            AgentRunStepStatus status = step == null ? AgentRunStepStatus.STARTED : step.status();
            if (status == AgentRunStepStatus.FAILED) {
                return AgentRunStepStatus.FAILED;
            }
            if (status == AgentRunStepStatus.STARTED) {
                hasStarted = true;
            }
            if (status != AgentRunStepStatus.SKIPPED) {
                allSkipped = false;
            }
        }
        if (hasStarted) {
            return AgentRunStepStatus.STARTED;
        }
        if (allSkipped) {
            return AgentRunStepStatus.SKIPPED;
        }
        return AgentRunStepStatus.SUCCESS;
    }
}
