package com.example.spring.wechat.conversation;

import com.example.spring.chat.ChatService;
import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.image.generation.ImageGenerationService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.client.VoiceSourceType;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.wechat.client.WechatIncomingVoice;
import com.example.spring.wechat.voice.VoiceRecognitionException;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;
import com.example.spring.wechat.voice.service.VoiceRecognitionService;
import com.example.spring.weather.WeatherResult;
import com.example.spring.weather.WeatherService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatVoiceConversationServiceTests {

    @Test
    void routesRecognizedVoiceTextThroughWeatherTool() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        VoiceRecognitionService voiceRecognitionService = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = sampleVoice();
        when(voiceRecognitionService.recognize(voice)).thenReturn(new VoiceRecognitionResult(
                "杭州今天天气怎么样",
                "zh",
                null,
                2000,
                "TEST"));
        when(weatherService.query("杭州")).thenReturn(sampleWeather("杭州"));
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("我听到你说：杭州今天天气怎么样\n\n杭州今天小雨，建议带伞。");
            return null;
        }).when(chatService).streamReply(anyString(), any());
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                voiceRecognitionService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(voice)));

        assertThat(reply.text()).contains("我听到你说：杭州今天天气怎么样").contains("带伞");
        verify(voiceRecognitionService).recognize(voice);
        verify(weatherService).query("杭州");
    }

    @Test
    void routesRecognizedVoiceTextThroughImageGenerationTool() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        VoiceRecognitionService voiceRecognitionService = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = sampleVoice();
        when(voiceRecognitionService.recognize(voice)).thenReturn(new VoiceRecognitionResult(
                "帮我画一只赛博朋克风格的橘猫",
                "zh",
                null,
                2000,
                "TEST"));
        when(imageGenerationService.generate(any())).thenReturn(new ImageGenerationResult(
                "一只赛博朋克风格的橘猫",
                "https://cdn.example.com/cat.png",
                "PNG".getBytes(),
                "cat.png",
                "image/png",
                null,
                null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                voiceRecognitionService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(voice)));

        assertThat(reply.text()).contains("我听到你说：帮我画一只赛博朋克风格的橘猫");
        assertThat(reply.hasImage()).isTrue();
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("赛博朋克风格的橘猫")));
    }

    @Test
    void routesNaturalVoiceImageRequestThroughImageGenerationTool() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        VoiceRecognitionService voiceRecognitionService = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = sampleVoice();
        when(voiceRecognitionService.recognize(voice)).thenReturn(new VoiceRecognitionResult(
                "帮我生成一张打工人工作的图片",
                "zh",
                null,
                2000,
                "TEST"));
        when(imageGenerationService.generate(any())).thenReturn(new ImageGenerationResult(
                "打工人工作的图片",
                "https://cdn.example.com/workers.png",
                "PNG".getBytes(),
                "workers.png",
                "image/png",
                null,
                null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                voiceRecognitionService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(voice)));

        assertThat(reply.hasImage()).isTrue();
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("打工人工作的图片")));
    }

    @Test
    void routesRecognizedVoiceTextThroughNormalChat() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        VoiceRecognitionService voiceRecognitionService = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = sampleVoice();
        when(voiceRecognitionService.recognize(voice)).thenReturn(new VoiceRecognitionResult(
                "你是谁",
                "zh",
                null,
                2000,
                "TEST"));
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("我是你的 AI 助手。");
            return null;
        }).when(chatService).streamReply(anyString(), any());
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                voiceRecognitionService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(voice)));

        assertThat(reply.text()).contains("我听到你说：你是谁").contains("我是你的 AI 助手");
    }

    @Test
    void returnsFriendlyMessageWhenVoiceRecognitionFails() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        VoiceRecognitionService voiceRecognitionService = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = sampleVoice();
        when(voiceRecognitionService.recognize(voice))
                .thenThrow(new VoiceRecognitionException("语音识别服务暂时不可用"));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                voiceRecognitionService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(voice)));

        assertThat(reply.text()).contains("语音识别失败").contains("语音识别服务暂时不可用");
    }

    private WechatIncomingVoice sampleVoice() {
        return new WechatIncomingVoice(
                VoiceSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/voice/1",
                "VOICE".getBytes(),
                "audio/silk",
                "voice.silk",
                2000,
                16000,
                "silk",
                null);
    }

    private WeatherResult sampleWeather(String city) {
        return new WeatherResult(
                "浙江",
                city,
                "小雨",
                "27",
                "东南",
                "3",
                "68",
                "2026-07-17 10:00:00",
                List.of());
    }
}
