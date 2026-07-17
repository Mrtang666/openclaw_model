package com.example.spring.agent;

import java.util.List;

public record AgentPlan(List<AgentType> steps) {
    public AgentPlan {
        if (steps == null || steps.isEmpty()) {
            steps = List.of(AgentType.CHAT);
        } else {
            steps = List.copyOf(steps);
        }
    }

    public AgentType primaryType() {
        return steps.get(0);
    }
}
