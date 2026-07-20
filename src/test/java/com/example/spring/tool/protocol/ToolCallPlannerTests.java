package com.example.spring.tool.protocol;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallPlannerTests {

    @Test
    void asksModelToReturnJsonToolPlanUsingRegisteredTools() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "tasks": [
                    {"tool": "weather", "arguments": {"city": "杭州"}}
                  ]
                }
                """);
        ToolCallPlanner planner = new ToolCallPlanner(
                chatService,
                new ToolCallPlanParser(new com.fasterxml.jackson.databind.ObjectMapper()));

        ToolPlan plan = planner.plan(
                "帮我查杭州天气",
                List.of(new WechatToolDefinition("weather", "查询天气", List.of("city"))))
                .orElseThrow();

        assertThat(plan.tasks()).singleElement()
                .satisfies(task -> {
                    assertThat(task.tool()).isEqualTo("weather");
                    assertThat(task.arguments()).containsEntry("city", "杭州");
                });
        verify(chatService).reply(argThat(prompt ->
                prompt.contains("只输出 JSON")
                        && prompt.contains("weather")
                        && prompt.contains("查询天气")
                        && prompt.contains("帮我查杭州天气")));
    }

    @Test
    void parsesClarificationDecisionFromPlannerOutput() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "needs_clarification": true,
                  "clarification_question": "你想查哪个城市的天气？",
                  "tasks": [
                    {"tool": "weather", "arguments": {"city": ""}}
                  ]
                }
                """);
        ToolCallPlanner planner = new ToolCallPlanner(
                chatService,
                new ToolCallPlanParser(new com.fasterxml.jackson.databind.ObjectMapper()));

        ConversationIntentDecision decision = planner.planDecision(
                "帮我查天气",
                List.of(new WechatToolDefinition("weather", "查询天气", List.of("city"))),
                "最近对话：无")
                .orElseThrow();

        assertThat(decision.needsClarification()).isTrue();
        assertThat(decision.clarificationQuestion()).contains("哪个城市");
        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> assertThat(task.tool()).isEqualTo("weather"));
        verify(chatService).reply(argThat(prompt ->
                prompt.contains("needs_clarification")
                        && prompt.contains("clarification_question")
                        && prompt.contains("任务拆解 JSON")));
    }

    @Test
    void asksModelToMarkVoiceRequestsForPreviousAssistantReply() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {
                  "tasks": [
                    {"tool": "voice_synthesis", "arguments": {"source": "previous"}}
                  ]
                }
                """);
        ToolCallPlanner planner = new ToolCallPlanner(
                chatService,
                new ToolCallPlanParser(new com.fasterxml.jackson.databind.ObjectMapper()));

        ConversationIntentDecision decision = planner.planDecision(
                "Please read the previous reply aloud and send it to me",
                List.of(new WechatToolDefinition("voice_synthesis", "voice synthesis", List.of("text", "reply", "source"))),
                "user: hello assistant: previous reply")
                .orElseThrow();

        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> assertThat(task.arguments()).containsEntry("source", "previous"));
        verify(chatService).reply(argThat(prompt ->
                prompt.contains("voice_synthesis")
                        && prompt.contains("source")
                        && prompt.contains("previous")));
    }
}
