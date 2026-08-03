package com.example.spring.wechat.conversation.agent.policy;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AgentStopPolicy {

    private static final Set<String> TERMINAL_ACTION_TOOLS = Set.of(
            "taxi_service",
            "reminder_create",
            "reminder_create_after",
            "reminder_update",
            "reminder_cancel",
            "reminder_complete",
            "reminder_snooze",
            "food_delivery",
            "meituan_travel",
            "email_send",
            "email_text_send",
            "browser_screenshot",
            "care_agent");

    public boolean endsAgentTurnAfterExecution(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return TERMINAL_ACTION_TOOLS.contains(toolName);
    }
}
