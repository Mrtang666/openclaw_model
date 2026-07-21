package com.example.spring.wechat.conversation.agent;

import com.example.spring.tool.protocol.function.DashScopeFunctionCallingClient;
import com.example.spring.tool.protocol.function.FunctionCallingMessage;
import com.example.spring.tool.protocol.function.FunctionCallingModelResponse;
import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.tools.WechatTool;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionCallingAgentLoopTests {

    @Test
    void executesToolCallsReturnsToolResultToModelAndUsesFinalAssistantAnswer() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        WechatToolRegistry registry = new WechatToolRegistry(List.of(new FakeWeatherTool()));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_weather_1",
                                "weather",
                                Map.of("city", "Hangzhou"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "Hangzhou is sunny today, so it is suitable for going out.",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "Is Hangzhou suitable for going out today?",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.text()).isEqualTo("Hangzhou is sunny today, so it is suitable for going out.");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionCallingMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, org.mockito.Mockito.times(2)).chat(messagesCaptor.capture(), anyList());
        List<FunctionCallingMessage> secondRoundMessages = messagesCaptor.getAllValues().get(1);
        assertThat(secondRoundMessages)
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("tool");
                    assertThat(message.toolCallId()).isEqualTo("call_weather_1");
                    assertThat(message.content()).contains("weather result for Hangzhou");
                });
    }

    @Test
    void sendsValidationFailureBackToModelWithoutExecutingInvalidToolCall() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeWeatherTool weatherTool = new FakeWeatherTool();
        WechatToolRegistry registry = new WechatToolRegistry(List.of(weatherTool));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_weather_1",
                                "weather",
                                Map.of())))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "Which city would you like to check?",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "Check the weather",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.text()).isEqualTo("Which city would you like to check?");
        assertThat(weatherTool.called).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionCallingMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, org.mockito.Mockito.times(2)).chat(messagesCaptor.capture(), anyList());
        List<FunctionCallingMessage> secondRoundMessages = messagesCaptor.getAllValues().get(1);
        assertThat(secondRoundMessages)
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("tool");
                    assertThat(message.content()).contains("city");
                });
    }

    private static final class FakeWeatherTool implements WechatTool {

        private boolean called;

        @Override
        public String name() {
            return "weather";
        }

        @Override
        public String description() {
            return "query weather";
        }

        @Override
        public List<String> arguments() {
            return List.of("city");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("city", "city name", "Hangzhou"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            called = true;
            return WechatReply.text("weather result for " + request.argument("city") + ": sunny");
        }
    }
}
