package com.example.spring.agent.trace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentTraceAccessPolicy {

    private final String configuredApiKey;

    public AgentTraceAccessPolicy(@Value("${agent.trace.diagnostic.api-key:}") String configuredApiKey) {
        this.configuredApiKey = clean(configuredApiKey);
    }

    public AgentTraceAccessDecision authorize(String providedApiKey) {
        if (configuredApiKey.isBlank()) {
            return new AgentTraceAccessDecision(true, "API_KEY_NOT_CONFIGURED");
        }
        String cleanProvidedApiKey = clean(providedApiKey);
        if (cleanProvidedApiKey.isBlank()) {
            return new AgentTraceAccessDecision(false, "API_KEY_MISSING");
        }
        if (configuredApiKey.equals(cleanProvidedApiKey)) {
            return new AgentTraceAccessDecision(true, "API_KEY_MATCHED");
        }
        return new AgentTraceAccessDecision(false, "API_KEY_MISMATCH");
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
