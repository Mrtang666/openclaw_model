package com.example.spring.document;

import com.example.spring.agent.AgentRequest;

public interface DocumentTaskPlanner {
    DocumentTaskPlan plan(AgentRequest request, String normalizedInstruction) throws Exception;
}
