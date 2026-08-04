package com.example.spring.wechat.conversation.agent.policy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentStopPolicy {

    private final ToolCapabilityPolicy toolCapabilityPolicy;

    public AgentStopPolicy() {
        this(new ToolCapabilityPolicy());
    }

    @Autowired
    public AgentStopPolicy(ToolCapabilityPolicy toolCapabilityPolicy) {
        this.toolCapabilityPolicy = toolCapabilityPolicy == null ? new ToolCapabilityPolicy() : toolCapabilityPolicy;
    }

    public boolean endsAgentTurnAfterExecution(String toolName) {
        return toolCapabilityPolicy.endsAgentTurnAfterExecution(toolName);
    }
}
