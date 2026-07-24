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
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
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

    @Test
    void doesNotExposeDuplicateVoiceFilesWhenModelCallsVoiceSynthesisAgainAfterToolResult() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeVoiceTool voiceTool = new FakeVoiceTool();
        WechatToolRegistry registry = new WechatToolRegistry(List.of(voiceTool));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_voice_1",
                                "voice_synthesis",
                                Map.of("text", "南京明天天气适合出门。"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_voice_2",
                                "voice_synthesis",
                                Map.of("text", "南京明天天气适合出门。"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "语音已经生成。",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "帮我查看明日的南京天气，用语音来回答我",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts()).extracting(part -> part.voice().fileName())
                .containsExactly("reply-1.mp3", "reply-2.mp3");
        assertThat(voiceTool.callCount).isEqualTo(1);
    }

    @Test
    void keepsOnlyLatestVoiceBundleWhenModelCallsWeatherThenVoiceTwice() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeWeatherTool weatherTool = new FakeWeatherTool();
        FakeVoiceTool voiceTool = new FakeVoiceTool();
        WechatToolRegistry registry = new WechatToolRegistry(List.of(weatherTool, voiceTool));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_weather_1",
                                "weather",
                                Map.of("city", "南京"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_voice_1",
                                "voice_synthesis",
                                Map.of("text", "南京明天天气初步播报。"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_weather_2",
                                "weather",
                                Map.of("city", "南京"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_voice_2",
                                "voice_synthesis",
                                Map.of("text", "南京明天天气最终播报。"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "已经用语音为你播报明日南京天气啦。",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "帮我查看明日的南京天气，用语音来回答我",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts()).allSatisfy(part ->
                assertThat(part.voice().transcriptText()).contains("最终播报").doesNotContain("初步播报"));
        assertThat(voiceTool.callCount).isEqualTo(2);
    }

    @Test
    void keepsOnlyLatestImageBundleWhenModelCallsImageGenerationTwice() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeImageTool imageTool = new FakeImageTool();
        WechatToolRegistry registry = new WechatToolRegistry(List.of(imageTool));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_image_1",
                                "image_generation",
                                Map.of("prompt", "first prompt"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_image_2",
                                "image_generation",
                                Map.of("prompt", "better prompt"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "图片已经生成好了。",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "帮我生成一张猫的图片",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasImage()).isTrue();
        assertThat(reply.parts().get(0).image().fileName()).isEqualTo("image-2.png");
        assertThat(imageTool.callCount).isEqualTo(2);
    }

    @Test
    void returnsLastToolFailureInsteadOfGenericMaxLoopMessageWhenToolKeepsFailing() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        WechatToolRegistry registry = new WechatToolRegistry(List.of(new FakeFailingWebSearchTool()));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_search_1",
                                "web_search",
                                Map.of("query", "Qdrant Java 接入方式"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_search_2",
                                "web_search",
                                Map.of("query", "Qdrant Java 接入方式"))))));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "帮我搜索Qdrant Java 接入方式",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.text()).contains("工具执行失败", "百炼 WebSearch 未返回可用搜索结果");
        assertThat(reply.text()).doesNotContain("步骤比较多");
        verify(client, org.mockito.Mockito.times(2)).chat(anyList(), anyList());
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

    private static final class FakeVoiceTool implements WechatTool {

        private int callCount;

        @Override
        public String name() {
            return "voice_synthesis";
        }

        @Override
        public String description() {
            return "synthesize voice";
        }

        @Override
        public List<String> arguments() {
            return List.of("text");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("text", "voice text", "hello"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            callCount++;
            return WechatReply.ordered(List.of(
                    WechatReply.Part.voice(new WechatReply.Voice(
                            "VOICE-1".getBytes(),
                            "reply-1.mp3",
                            2_000,
                            16_000,
                            null,
                            null,
                            request.argument("text"))),
                    WechatReply.Part.voice(new WechatReply.Voice(
                            "VOICE-2".getBytes(),
                            "reply-2.mp3",
                            2_000,
                            16_000,
                            null,
                            null,
                            request.argument("text")))));
        }
    }

    private static final class FakeImageTool implements WechatTool {

        private int callCount;

        @Override
        public String name() {
            return "image_generation";
        }

        @Override
        public String description() {
            return "generate image";
        }

        @Override
        public List<String> arguments() {
            return List.of("prompt");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("prompt", "image prompt", "cat"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            callCount++;
            ImageGenerationResult image = new ImageGenerationResult(
                    request.argument("prompt"),
                    "https://example.com/image-" + callCount + ".png",
                    ("IMAGE-" + callCount).getBytes(),
                    "image-" + callCount + ".png",
                    "image/png",
                    1024,
                    1024);
            return WechatReply.ordered(List.of(WechatReply.Part.image("图片已生成", image)));
        }
    }

    private static final class FakeFailingWebSearchTool implements WechatTool {

        @Override
        public String name() {
            return "web_search";
        }

        @Override
        public String description() {
            return "search web";
        }

        @Override
        public List<String> arguments() {
            return List.of("query");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("query", "search query", "Qdrant"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            throw new RuntimeException("百炼 WebSearch 未返回可用搜索结果");
        }
    }
}
