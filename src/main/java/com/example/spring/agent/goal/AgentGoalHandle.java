package com.example.spring.agent.goal;

public record AgentGoalHandle(long goalId) {

    public AgentGoalHandle {
        if (goalId <= 0) {
            throw new IllegalArgumentException("goalId must be positive");
        }
    }
}
