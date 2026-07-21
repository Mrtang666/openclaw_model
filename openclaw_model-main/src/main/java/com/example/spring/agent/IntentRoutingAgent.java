package com.example.spring.agent;

public interface IntentRoutingAgent {
    IntentRoutingDecision route(AgentRequest request) throws Exception;
}
