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
    void preservesMapTextAndImageFromMapTool() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        WechatToolRegistry registry = new WechatToolRegistry(List.of(new FakeMapTool()));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 3);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_map_1",
                                "map_search",
                                Map.of("operation", "multi_route"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "路线已经规划完成。",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "规划杭州东站、西湖和灵隐寺路线",
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
        assertThat(reply.parts().get(0).text()).contains("杭州东站 → 西湖 → 灵隐寺");
        assertThat(reply.parts().get(1).hasImage()).isTrue();
        assertThat(reply.parts().get(1).image().fileName()).isEqualTo("route-map.png");
    }

    @Test
    void stopsImmediatelyAndShowsMapAmbiguityInsteadOfExhaustingLoopRounds() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        WechatToolRegistry registry = new WechatToolRegistry(List.of(new FakeFailingMapTool()));
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, registry, 5);

        when(client.chat(anyList(), anyList()))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_map_ambiguity",
                                "map_search",
                                Map.of("operation", "multi_route"))))));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "规划杭州多个地点路线",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.text())
                .contains("地图查询失败")
                .contains("存在歧义")
                .doesNotContain("把需求拆短");
        verify(client, org.mockito.Mockito.times(1)).chat(anyList(), anyList());
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

    private static final class FakeMapTool implements WechatTool {

        @Override
        public String name() {
            return "map_search";
        }

        @Override
        public String description() {
            return "plan route";
        }

        @Override
        public List<String> arguments() {
            return List.of("operation");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("operation", "map operation", "multi_route"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            ImageGenerationResult image = new ImageGenerationResult(
                    "route-map",
                    "",
                    "MAP".getBytes(),
                    "route-map.png",
                    "image/png",
                    800,
                    700);
            return WechatReply.ordered(List.of(
                    WechatReply.Part.text("杭州东站 → 西湖 → 灵隐寺"),
                    WechatReply.Part.image("完整路线图", image)));
        }
    }

    private static final class FakeFailingMapTool implements WechatTool {

        @Override
        public String name() {
            return "map_search";
        }

        @Override
        public String description() {
            return "plan route";
        }

        @Override
        public List<String> arguments() {
            return List.of("operation");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(WechatToolParameter.requiredString("operation", "map operation", "multi_route"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            return WechatReply.text("地图查询失败：地点存在歧义，请补充城市或详细地址：候选一、候选二");
        }
    }
}
