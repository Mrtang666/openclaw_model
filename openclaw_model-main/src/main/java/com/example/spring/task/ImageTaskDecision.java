package com.example.spring.task;

import com.example.spring.agent.AgentPlan;
import com.example.spring.agent.AgentRequest;

public record ImageTaskDecision(
    TaskDecisionAction action,
    String reply,
    AgentRequest request,
    AgentPlan plan) {

    public static ImageTaskDecision passThrough(AgentRequest request, AgentPlan plan) {
        return new ImageTaskDecision(TaskDecisionAction.PASS_THROUGH, "", request, plan);
    }

    public static ImageTaskDecision reply(String text, AgentRequest request, AgentPlan plan) {
        return new ImageTaskDecision(TaskDecisionAction.REPLY, text, request, plan);
    }

    public static ImageTaskDecision execute(AgentRequest request, AgentPlan plan) {
        return new ImageTaskDecision(TaskDecisionAction.EXECUTE, "", request, plan);
    }
}
