package com.example.spring.agent.trace;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentRunDiagnosticMapper {

    private final AgentTraceRedactionPolicy redactionPolicy;

    public AgentRunDiagnosticMapper() {
        this(new AgentTraceRedactionPolicy());
    }

    AgentRunDiagnosticMapper(AgentTraceRedactionPolicy redactionPolicy) {
        this.redactionPolicy = redactionPolicy == null ? new AgentTraceRedactionPolicy() : redactionPolicy;
    }

    public AgentRunDiagnosticTraceView toDiagnostic(AgentRunTraceView trace) {
        if (trace == null) {
            return null;
        }
        List<AgentRunDiagnosticStepView> steps = trace.steps().stream()
                .map(this::toDiagnostic)
                .toList();
        return new AgentRunDiagnosticTraceView(
                trace.runId(),
                trace.runKey(),
                trace.channel(),
                trace.sessionKey(),
                redact(trace.userText()),
                redact(trace.contextSummary()),
                trace.status(),
                trace.stopReason(),
                redact(trace.finalReplySummary()),
                trace.startedAt(),
                trace.completedAt(),
                steps);
    }

    public AgentRunDiagnosticSummaryView toDiagnostic(AgentRunSummaryView summary) {
        if (summary == null) {
            return null;
        }
        return new AgentRunDiagnosticSummaryView(
                summary.runId(),
                summary.runKey(),
                summary.channel(),
                summary.sessionKey(),
                redact(summary.userText()),
                redact(summary.contextSummary()),
                summary.status(),
                summary.stopReason(),
                redact(summary.finalReplySummary()),
                summary.startedAt(),
                summary.completedAt());
    }

    private AgentRunDiagnosticStepView toDiagnostic(AgentRunStepView step) {
        return new AgentRunDiagnosticStepView(
                step.stepId(),
                step.stepIndex(),
                step.stepType(),
                step.roundNumber(),
                step.toolName(),
                step.status(),
                redact(step.inputSummary()),
                redact(step.outputSummary()),
                redact(step.metadataJson()),
                step.createdAt());
    }

    private String redact(String value) {
        return redactionPolicy.redact(value);
    }
}
