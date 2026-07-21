package com.example.spring.tool.protocol;

import com.example.spring.tool.protocol.legacy.ToolCall;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableConversationToolPlannerTests {

    @Test
    void usesPromptJsonPlannerByDefault() {
        FakePlanner promptJson = new FakePlanner("weather");
        FakePlanner functionCalling = new FakePlanner("chat");
        ConfigurableConversationToolPlanner planner = new ConfigurableConversationToolPlanner(
                promptJson,
                functionCalling,
                "");

        ConversationIntentDecision decision = planner.planDecision(
                "帮我查杭州天气",
                sampleDefinitions(),
                "无")
                .orElseThrow();

        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> assertThat(task.tool()).isEqualTo("weather"));
        assertThat(promptJson.called).isTrue();
        assertThat(functionCalling.called).isFalse();
    }

    @Test
    void usesFunctionCallingPlannerWhenConfigured() {
        FakePlanner promptJson = new FakePlanner("weather");
        FakePlanner functionCalling = new FakePlanner("image_generation");
        ConfigurableConversationToolPlanner planner = new ConfigurableConversationToolPlanner(
                promptJson,
                functionCalling,
                "function-calling");

        ConversationIntentDecision decision = planner.planDecision(
                "帮我画一只橘猫",
                sampleDefinitions(),
                "无")
                .orElseThrow();

        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> assertThat(task.tool()).isEqualTo("image_generation"));
        assertThat(promptJson.called).isFalse();
        assertThat(functionCalling.called).isTrue();
    }

    @Test
    void fallsBackToPromptJsonForUnknownMode() {
        FakePlanner promptJson = new FakePlanner("chat");
        FakePlanner functionCalling = new FakePlanner("weather");
        ConfigurableConversationToolPlanner planner = new ConfigurableConversationToolPlanner(
                promptJson,
                functionCalling,
                "unknown");

        ConversationIntentDecision decision = planner.planDecision("你好", sampleDefinitions(), "无").orElseThrow();

        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> assertThat(task.tool()).isEqualTo("chat"));
        assertThat(promptJson.called).isTrue();
        assertThat(functionCalling.called).isFalse();
    }

    private List<WechatToolDefinition> sampleDefinitions() {
        return List.of(new WechatToolDefinition(
                "weather",
                "查询天气",
                List.of(WechatToolParameter.requiredString("city", "城市名", "杭州"))));
    }

    private static final class FakePlanner implements ConversationToolPlanner {

        private final String toolName;
        private boolean called;

        private FakePlanner(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public Optional<ConversationIntentDecision> planDecision(
                String userText,
                List<WechatToolDefinition> toolDefinitions,
                String historyText) {
            called = true;
            return Optional.of(new ConversationIntentDecision(
                    List.of(new ToolCall(toolName, java.util.Map.of())),
                    false,
                    ""));
        }
    }
}
