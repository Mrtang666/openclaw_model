package com.example.spring.wechat.conversation.agent.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStopPolicyTests {

    private final AgentStopPolicy policy = new AgentStopPolicy();

    @Test
    void endsAgentTurnAfterTerminalActionToolExecutes() {
        List<String> terminalTools = List.of(
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

        assertThat(terminalTools)
                .allSatisfy(toolName -> assertThat(policy.endsAgentTurnAfterExecution(toolName))
                        .as(toolName)
                        .isTrue());
    }

    @Test
    void continuesAgentTurnAfterNonTerminalToolExecutes() {
        assertThat(policy.endsAgentTurnAfterExecution("web_search")).isFalse();
        assertThat(policy.endsAgentTurnAfterExecution("knowledge_query")).isFalse();
        assertThat(policy.endsAgentTurnAfterExecution("")).isFalse();
        assertThat(policy.endsAgentTurnAfterExecution(null)).isFalse();
    }
}
