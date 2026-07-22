package com.example.spring.tool.protocol;

import com.example.spring.tool.protocol.legacy.ToolCallPlanParser;
import com.example.spring.tool.protocol.legacy.ToolPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallPlanParserTests {

    @Test
    void parsesStructuredToolCallJson() {
        ToolCallPlanParser parser = new ToolCallPlanParser(new ObjectMapper());

        ToolPlan plan = parser.parse("""
                {
                  "tasks": [
                    {
                      "tool": "image_generation",
                      "arguments": {
                        "prompt": "一只小猫躺在床上",
                        "optimize_prompt": "true"
                      }
                    },
                    {
                      "tool": "weather",
                      "arguments": {
                        "city": "杭州"
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(plan.tasks()).hasSize(2);
        assertThat(plan.tasks().get(0).tool()).isEqualTo("image_generation");
        assertThat(plan.tasks().get(0).arguments()).containsEntry("prompt", "一只小猫躺在床上");
        assertThat(plan.tasks().get(1).tool()).isEqualTo("weather");
        assertThat(plan.tasks().get(1).arguments()).containsEntry("city", "杭州");
    }

    @Test
    void extractsJsonWhenModelWrapsItWithText() {
        ToolCallPlanParser parser = new ToolCallPlanParser(new ObjectMapper());

        ToolPlan plan = parser.parse("""
                下面是任务计划：
                {
                  "tasks": [
                    {"tool": "chat", "arguments": {"message": "制定出行计划"}}
                  ]
                }
                请执行。
                """).orElseThrow();

        assertThat(plan.tasks()).singleElement()
                .satisfies(task -> {
                    assertThat(task.tool()).isEqualTo("chat");
                    assertThat(task.arguments()).containsEntry("message", "制定出行计划");
                });
    }

    @Test
    void parsesClarificationDecisionJson() {
        ToolCallPlanParser parser = new ToolCallPlanParser(new ObjectMapper());

        ConversationIntentDecision decision = parser.parseDecision("""
                {
                  "needs_clarification": true,
                  "clarification_question": "你想查哪个城市的天气？",
                  "tasks": [
                    {
                      "tool": "weather",
                      "arguments": {
                        "city": ""
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(decision.needsClarification()).isTrue();
        assertThat(decision.clarificationQuestion()).isEqualTo("你想查哪个城市的天气？");
        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> {
                    assertThat(task.tool()).isEqualTo("weather");
                    assertThat(task.arguments()).containsEntry("city", "");
                });
    }
}
