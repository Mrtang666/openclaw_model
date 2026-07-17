package com.example.spring.wechat.conversation;

import com.example.spring.chat.ChatService;
import com.example.spring.exception.WeatherServiceException;
import com.example.spring.image.generation.ImageGenerationRequest;
import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.image.generation.ImageGenerationService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.weather.WeatherResult;
import com.example.spring.weather.WeatherService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatConversationServiceTests {

    @Test
    void routesNaturalWeatherQuestionThroughWeatherApiAndChatModel() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        WeatherResult weatherResult = sampleWeatherResult("杭州");
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("今天杭州适合出门，记得带伞。");
            return null;
        }).when(chatService).streamReply(any(), any());
        org.mockito.Mockito.when(weatherService.query("杭州")).thenReturn(weatherResult);
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String reply = service.handle("帮我查看今天杭州的天气怎么样");

        assertThat(reply).isEqualTo("今天杭州适合出门，记得带伞。");
        verify(weatherService).query("杭州");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("杭州")
                        && prompt.contains("27")
                        && prompt.contains("小雨")
                        && prompt.contains("带伞")), any());
    }

    @Test
    void routesNormalConversationToChatModel() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("我是");
            emitter.emit("你的 AI 助手");
            return null;
        }).when(chatService).streamReply(anyString(), any());
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String reply = service.handle("你是谁");

        assertThat(reply).isEqualTo("我是你的 AI 助手");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("你是谁")), any());
    }

    @Test
    void fallsBackToModelWhenWeatherServiceFails() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        org.mockito.Mockito.when(weatherService.query("杭州"))
                .thenThrow(new WeatherServiceException("天气服务暂时不可用"));
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("抱歉，天气暂时查不到。");
            return null;
        }).when(chatService).streamReply(any(), any());
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String reply = service.handle("帮我查看今天杭州的天气怎么样");

        assertThat(reply).isEqualTo("抱歉，天气暂时查不到。");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("天气服务暂时不可用")
                        && prompt.contains("杭州")), any());
    }

    @Test
    void remembersPreviousTurnsForTheSameConversation() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("收到");
            return null;
        }).when(chatService).streamReply(any(), any());
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String firstReply = service.handle("我喜欢美食");
        String secondReply = service.handle("可以，偏好美食");

        assertThat(firstReply).isEqualTo("收到");
        assertThat(secondReply).isEqualTo("收到");
        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatService, times(2)).streamReply(promptCaptor.capture(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).contains("我喜欢美食");
        assertThat(prompts.get(1))
                .contains("我喜欢美食")
                .contains("可以，偏好美食");
    }

    @Test
    void routesImageGenerationRequestToImageService() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "帮我画一只赛博朋克风格的橘猫",
                        "https://cdn.example.com/generated.png",
                        "PNG".getBytes(),
                        "generated.png",
                        "image/png",
                        null,
                        null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "帮我画一只赛博朋克风格的橘猫"));

        assertThat(reply.text()).contains("图片");
        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/generated.png");
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("赛博朋克风格的橘猫")));
    }

    @Test
    void routesImageRefinementAfterGeneratedImageToImageService() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一只赛博朋克风格的橘猫，眼睛有蓝色灯光",
                        "https://cdn.example.com/first.png",
                        "FIRST".getBytes(),
                        "first.png",
                        "image/png",
                        null,
                        null))
                .thenReturn(new ImageGenerationResult(
                        "一只赛博朋克风格的橘猫，眼睛不要有灯光",
                        "https://cdn.example.com/second.png",
                        "SECOND".getBytes(),
                        "second.png",
                        "image/png",
                        null,
                        null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        service.handleWechat(new WechatIncomingMessage("user-1", "帮我画一只赛博朋克风格的橘猫，眼睛有蓝色灯光"));
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "眼睛不要有灯光"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/second.png");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(imageGenerationService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).prompt())
                .contains("赛博朋克风格的橘猫")
                .contains("眼睛不要有灯光");
        verify(chatService, never()).streamReply(any(), any());
    }

    @Test
    void regeneratesImageWhenUserConfirmsGenerationAfterImageContext() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一只赛博朋克风格的橘猫",
                        "https://cdn.example.com/first.png",
                        "FIRST".getBytes(),
                        "first.png",
                        "image/png",
                        null,
                        null))
                .thenReturn(new ImageGenerationResult(
                        "一只赛博朋克风格的橘猫",
                        "https://cdn.example.com/second.png",
                        "SECOND".getBytes(),
                        "second.png",
                        "image/png",
                        null,
                        null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        service.handleWechat(new WechatIncomingMessage("user-1", "帮我画一只赛博朋克风格的橘猫"));
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "你生成呀"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/second.png");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(imageGenerationService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).prompt())
                .contains("赛博朋克风格的橘猫")
                .contains("重新生成");
        verify(chatService, never()).streamReply(any(), any());
    }

    private WeatherResult sampleWeatherResult(String city) {
        return new WeatherResult(
                "浙江",
                city,
                "小雨",
                "27",
                "东南",
                "3",
                "68",
                "2026-07-17 10:00:00",
                List.of(new WeatherResult.Forecast(
                        "2026-07-17", "4", "小雨", "多云", "29", "24",
                        "东南", "东北", "3-4", "1-2")));
    }
}
