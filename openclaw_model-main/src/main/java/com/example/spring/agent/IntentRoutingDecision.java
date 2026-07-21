package com.example.spring.agent;

import com.example.spring.document.DocumentTaskPlan;

public record IntentRoutingDecision(
    AgentPlan plan,
    DocumentTaskPlan documentTaskPlan,
    boolean semantic) {

    public IntentRoutingDecision {
        plan = plan == null ? new AgentPlan(null) : plan;
    }
}
