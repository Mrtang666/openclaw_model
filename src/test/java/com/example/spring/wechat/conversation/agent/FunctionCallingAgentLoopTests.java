package com.example.spring.wechat.conversation.agent;

import com.example.spring.tool.protocol.function.DashScopeFunctionCallingClient;
import com.example.spring.tool.protocol.function.FunctionCallingMessage;
import com.example.spring.tool.protocol.function.FunctionCallingModelResponse;
import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.WechatConversationMode;
import com.example.spring.wechat.conversation.tools.WechatTool;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    void appliesDifferentSystemPromptsForPatientCaregiverAndDoctorModes() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(
                client,
                new WechatToolRegistry(List.of(new FakeReminderAfterTool(false))),
                5);
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(
                new FunctionCallingModelResponse("ok", List.of())));

        loop.run(request("你好", WechatConversationMode.PATIENT)).orElseThrow();
        loop.run(request("你好", WechatConversationMode.CAREGIVER)).orElseThrow();
        loop.run(request("你好", WechatConversationMode.DOCTOR)).orElseThrow();
        loop.run(request("你好", WechatConversationMode.GENERAL)).orElseThrow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionCallingMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, org.mockito.Mockito.times(4)).chat(messagesCaptor.capture(), anyList());

        List<List<FunctionCallingMessage>> calls = messagesCaptor.getAllValues();
        assertThat(calls.get(0).get(0).content())
                .contains("当前对话模式：患者端")
                .contains("一次只推进一件最重要的事");
        assertThat(calls.get(1).get(0).content())
                .contains("当前对话模式：家属端")
                .contains("当前情况、需要关注、建议行动");
        assertThat(calls.get(2).get(0).content())
                .contains("当前对话模式：医生端")
                .contains("摘要、关键变化、风险/告警、依从性、待处理事项");
        assertThat(calls.get(3).get(0).content()).doesNotContain("当前对话模式：");
    }

    @Test
    void runtimePromptProvidesServerTimeAndRequiresRelativeReminderTool() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(
                client,
                new WechatToolRegistry(List.of(new FakeReminderAfterTool(false))),
                5,
                Clock.fixed(Instant.parse("2026-07-27T07:58:08Z"), ZoneOffset.UTC),
                "Asia/Shanghai");
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(
                new FunctionCallingModelResponse("ok", List.of())));

        loop.run(request("两分钟后提醒我去喝水", (a, b, c, d) -> {
        })).orElseThrow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionCallingMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).chat(messagesCaptor.capture(), anyList());
        assertThat(messagesCaptor.getValue().get(0).content())
                .contains("服务器当前时间：2026-07-27T15:58:08+08:00")
                .contains("必须调用 reminder_create_after")
                .contains("原样提取 delay_value 和 delay_unit")
                .contains("禁止换算分钟或 execute_at");
    }

    @Test
    void relativeReminderExecutesOnlyFirstMutationAndReturnsAuthoritativeResult() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeReminderAfterTool reminder = new FakeReminderAfterTool(false);
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, new WechatToolRegistry(List.of(reminder)), 5);
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(new FunctionCallingModelResponse(
                "",
                List.of(
                        new FunctionCallingToolCall(
                                "reminder-1", "reminder_create_after", Map.of(
                                        "title", "喝水", "delay_value", "2", "delay_unit", "minutes")),
                        new FunctionCallingToolCall(
                                "reminder-2", "reminder_create_after", Map.of(
                                        "title", "喝水", "delay_value", "2", "delay_unit", "minutes"))))));

        WechatReply reply = loop.run(request("两分钟后提醒我去喝水", (a, b, c, d) -> {
        })).orElseThrow();

        assertThat(reply.text()).isEqualTo("已创建提醒 #8，将于 2026-07-27 16:00:08 提醒：喝水");
        assertThat(reminder.callCount).isEqualTo(1);
        verify(client, org.mockito.Mockito.times(1)).chat(anyList(), anyList());
    }

    @Test
    void relativeReminderBusinessFailureIsRecordedAsFailedAndEndsTurn() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeReminderAfterTool reminder = new FakeReminderAfterTool(true);
        List<String> statuses = new ArrayList<>();
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, new WechatToolRegistry(List.of(reminder)), 5);
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(new FunctionCallingModelResponse(
                "",
                List.of(new FunctionCallingToolCall(
                        "reminder-1", "reminder_create_after", Map.of(
                                "title", "喝水", "delay_value", "0", "delay_unit", "minutes"))))));

        WechatReply reply = loop.run(request(
                "零分钟后提醒我去喝水", (a, b, c, status) -> statuses.add(status))).orElseThrow();

        assertThat(reply.text()).startsWith("提醒操作未完成：");
        assertThat(statuses).containsExactly("FAILED");
        assertThat(reminder.callCount).isEqualTo(1);
        verify(client, org.mockito.Mockito.times(1)).chat(anyList(), anyList());
    }

    @Test
    void meituanTravelResultIsReturnedDirectlyWithoutAnotherModelRound() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeMeituanTravelTool travel = new FakeMeituanTravelTool();
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(
                client, new WechatToolRegistry(List.of(travel)), 5);
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(new FunctionCallingModelResponse("",
                List.of(new FunctionCallingToolCall("travel-1", "meituan_travel", Map.of(
                        "query", "上海三日游",
                        "origin_query", "帮我规划上海三日游"))))));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "帮我规划上海三日游",
                "",
                List.of(),
                (a, b) -> { },
                (a, b) -> { },
                (a, b, c, d) -> { })).orElseThrow();

        assertThat(reply.text()).isEqualTo("## 美团官方结果\n\n[查看方案](https://hotel.meituan.com/test)");
        assertThat(travel.callCount).isEqualTo(1);
        verify(client, org.mockito.Mockito.times(1)).chat(anyList(), anyList());
    }

    @Test
    void taxiToolResultEndsCurrentAgentTurnWithoutAnotherModelRound() {
        DashScopeFunctionCallingClient client = mock(DashScopeFunctionCallingClient.class);
        FakeTaxiTool taxi = new FakeTaxiTool();
        FunctionCallingAgentLoop loop = new FunctionCallingAgentLoop(client, new WechatToolRegistry(List.of(taxi)), 5);
        when(client.chat(anyList(), anyList())).thenReturn(Optional.of(new FunctionCallingModelResponse("",
                List.of(new FunctionCallingToolCall("taxi-1", "taxi_service", Map.of("operation", "open_didi_app"))))));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest("user-1", "打开滴滴 1", "报价编号 quote-1",
                List.of(), (a,b)->{}, (a,b)->{}, (a,b,c,d)->{})).orElseThrow();

        assertThat(reply.text()).contains("滴滴链接");
        assertThat(taxi.callCount).isEqualTo(1);
        verify(client, org.mockito.Mockito.times(1)).chat(anyList(), anyList());
    }

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
    void skipsDuplicateSuccessfulToolCallAndReturnsCachedResultToModel() {
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
                                Map.of("city", "Hangzhou"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "",
                        List.of(new FunctionCallingToolCall(
                                "call_weather_2",
                                "weather",
                                Map.of("city", "Hangzhou"))))))
                .thenReturn(Optional.of(new FunctionCallingModelResponse(
                        "I reused the weather result.",
                        List.of())));

        WechatReply reply = loop.run(new FunctionCallingAgentRequest(
                "user-1",
                "Check Hangzhou weather twice",
                "No previous context",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                },
                (toolName, arguments, resultSummary, status) -> {
                }))
                .orElseThrow();

        assertThat(reply.text()).isEqualTo("I reused the weather result.");
        assertThat(weatherTool.callCount).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FunctionCallingMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, org.mockito.Mockito.times(3)).chat(messagesCaptor.capture(), anyList());
        List<FunctionCallingMessage> thirdRoundMessages = messagesCaptor.getAllValues().get(2);
        assertThat(thirdRoundMessages)
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("tool");
                    assertThat(message.toolCallId()).isEqualTo("call_weather_2");
                    assertThat(message.content()).contains("weather result for Hangzhou");
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
        private int callCount;

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
            callCount++;
            return WechatReply.text("weather result for " + request.argument("city") + ": sunny");
        }
    }

    private FunctionCallingAgentRequest request(
            String userText,
            FunctionCallingAgentRequest.ToolExecutionRecorder recorder) {
        return new FunctionCallingAgentRequest(
                "clawbot:connection-1:wechat-user-1",
                userText,
                "No previous context",
                List.of(),
                (a, b) -> {
                },
                (a, b) -> {
                },
                recorder);
    }

    private FunctionCallingAgentRequest request(String userText, WechatConversationMode conversationMode) {
        return new FunctionCallingAgentRequest(
                "clawbot:connection-1:wechat-user-1",
                userText,
                "No previous context",
                List.of(),
                List.of(),
                List.of(),
                (a, b) -> {
                },
                (a, b) -> {
                },
                (a, b, c, d) -> {
                },
                conversationMode);
    }

    private static final class FakeReminderAfterTool implements WechatTool {
        private final boolean fail;
        private int callCount;

        private FakeReminderAfterTool(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String name() {
            return "reminder_create_after";
        }

        @Override
        public String description() {
            return "create a reminder after a relative delay";
        }

        @Override
        public List<String> arguments() {
            return List.of("title", "delay_value", "delay_unit");
        }

        @Override
        public List<WechatToolParameter> parameters() {
            return List.of(
                    WechatToolParameter.requiredString("title", "reminder title", "喝水"),
                    new WechatToolParameter(
                            "delay_value", "integer", true, "relative delay", List.of(), "2"),
                    new WechatToolParameter(
                            "delay_unit", "string", true, "relative unit", List.of("minutes", "hours", "days"), "minutes"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            callCount++;
            if (fail) {
                return WechatReply.text("提醒操作未完成：delay_value 必须是正整数");
            }
            return WechatReply.text("已创建提醒 #8，将于 2026-07-27 16:00:08 提醒：喝水");
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

    private static final class FakeTaxiTool implements WechatTool {
        private int callCount;
        public String name(){return "taxi_service";}
        public String description(){return "taxi";}
        public List<String> arguments(){return List.of("operation");}
        public List<WechatToolParameter> parameters(){return List.of(WechatToolParameter.requiredString("operation","operation","open_didi_app"));}
        public WechatReply execute(WechatToolRequest request){callCount++;return WechatReply.text("滴滴链接：https://v.didi.cn/test");}
    }

    private static final class FakeMeituanTravelTool implements WechatTool {
        private int callCount;
        public String name(){return "meituan_travel";}
        public String description(){return "meituan travel";}
        public List<String> arguments(){return List.of("query", "origin_query");}
        public List<WechatToolParameter> parameters(){return List.of(
                WechatToolParameter.requiredString("query", "query", "上海三日游"),
                WechatToolParameter.requiredString("origin_query", "origin query", "帮我规划上海三日游"));}
        public WechatReply execute(WechatToolRequest request){
            callCount++;
            return WechatReply.text("## 美团官方结果\n\n[查看方案](https://hotel.meituan.com/test)");
        }
    }
}
