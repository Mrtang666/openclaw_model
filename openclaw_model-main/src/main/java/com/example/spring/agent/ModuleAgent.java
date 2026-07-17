package com.example.spring.agent;

public interface ModuleAgent {
    AgentType type();

    AgentResponse execute(AgentRequest request) throws Exception;
}
