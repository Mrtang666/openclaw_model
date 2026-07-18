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
import static org.mockito.Mockito.inOrder;
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
    void optimizesImagePromptWithChatModelBeforeGeneratingWhenUserRequestsBothSteps() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString())).thenReturn("一只小猫慵懒地躺在浅灰色布艺沙发上，暖色自然光，真实摄影风格，画面温馨干净。");
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一只小猫慵懒地躺在浅灰色布艺沙发上，暖色自然光，真实摄影风格，画面温馨干净。",
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
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "我想要生成一张图片，提示词是一个小猫躺在沙发上，先优化提示词，再生成图片"));

        assertThat(reply.preImageTexts()).containsExactly("优化后的图片提示词：\n一只小猫慵懒地躺在浅灰色布艺沙发上，暖色自然光，真实摄影风格，画面温馨干净。");
        assertThat(reply.text()).isEqualTo("我已经帮你生成好了，图片如下：");
        assertThat(reply.hasImage()).isTrue();
        verify(chatService).reply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("图片提示词优化器")
                        && prompt.contains("一个小猫躺在沙发上")
                        && prompt.contains("先优化提示词，再生成图片")));
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("小猫慵懒地躺在浅灰色布艺沙发上")
                        && request.prompt().contains("真实摄影风格")));
        var order = inOrder(chatService, imageGenerationService);
        order.verify(chatService).reply(anyString());
        order.verify(imageGenerationService).generate(any());
    }

    @Test
    void onlyReturnsOptimizedImagePromptWhenUserAsksToWaitForApproval() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString())).thenReturn("一只戴圆框眼镜的橘猫坐在书桌前看书，水彩插画风格，柔和光线。");
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "帮我生成一张橘猫看书的图，先优化提示词，等我允许后再生成图片"));

        assertThat(reply.hasImage()).isFalse();
        assertThat(reply.text()).contains("优化后的图片提示词")
                .contains("戴圆框眼镜的橘猫")
                .contains("如果确认要生成");
        verify(imageGenerationService, never()).generate(any());
    }

    @Test
    void generatesPendingOptimizedImagePromptWhenUserApprovesLater() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString())).thenReturn("一只戴圆框眼镜的橘猫坐在书桌前看书，水彩插画风格，柔和光线。");
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一只戴圆框眼镜的橘猫坐在书桌前看书，水彩插画风格，柔和光线。",
                        "https://cdn.example.com/reading-cat.png",
                        "PNG".getBytes(),
                        "reading-cat.png",
                        "image/png",
                        null,
                        null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "帮我生成一张橘猫看书的图，先优化提示词，等我允许后再生成图片"));
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "可以生成了"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.preImageTexts()).isEmpty();
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("戴圆框眼镜的橘猫")
                        && request.prompt().contains("水彩插画风格")));
    }

    @Test
    void handlesMultipleUserRequirementsInOrderAcrossImageWeatherAndPlanningTools() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString())).thenReturn("一只橘白相间的小猫躺在柔软床铺上，暖色自然光，真实摄影风格。");
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一只橘白相间的小猫躺在柔软床铺上，暖色自然光，真实摄影风格。",
                        "https://cdn.example.com/bed-cat.png",
                        "PNG".getBytes(),
                        "bed-cat.png",
                        "image/png",
                        null,
                        null));
        when(weatherService.query("杭州")).thenReturn(sampleWeatherResult("杭州"));
        doAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            if (prompt.contains("天气数据")) {
                emitter.emit("杭州今天小雨，建议带伞。");
            } else {
                emitter.emit("今日杭州出行计划：上午去室内展馆，中午就近吃饭，下午雨小再去西湖边散步。");
            }
            return null;
        }).when(chatService).streamReply(anyString(), any());
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "我想要生成一张图片，提示词是一个小猫躺在床上，先优化提示词，再生成图片，然后帮我查询一下杭州今天的天气，帮我生成一份今日杭州出行计划"));

        assertThat(reply.parts()).hasSize(4);
        assertThat(reply.parts().get(0).text()).contains("优化后的图片提示词").contains("小猫躺在柔软床铺上");
        assertThat(reply.parts().get(1).hasImage()).isTrue();
        assertThat(reply.parts().get(1).image().imageUrl()).isEqualTo("https://cdn.example.com/bed-cat.png");
        assertThat(reply.parts().get(2).text()).contains("杭州今天小雨").contains("带伞");
        assertThat(reply.parts().get(3).text()).contains("今日杭州出行计划").contains("西湖");
        verify(weatherService).query("杭州");
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("小猫躺在柔软床铺上")));
        var order = inOrder(chatService, imageGenerationService, weatherService);
        order.verify(chatService).reply(anyString());
        order.verify(imageGenerationService).generate(any());
        order.verify(weatherService).query("杭州");
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

    @Test
    void regeneratesImageWhenUserProvidesNaturalModificationAfterImageContext() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一张打工人工作照片",
                        "https://cdn.example.com/first.png",
                        "FIRST".getBytes(),
                        "first.png",
                        "image/png",
                        null,
                        null))
                .thenReturn(new ImageGenerationResult(
                        "一张更精神的打工人工作照片",
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

        service.handleWechat(new WechatIncomingMessage("user-1", "帮我生成一张打工人正在工作的照片"));
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "更精神一点，办公场景更高级一点"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/second.png");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(imageGenerationService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).prompt())
                .contains("打工人正在工作的照片")
                .contains("更精神一点")
                .contains("办公场景更高级一点");
        verify(chatService, never()).streamReply(any(), any());
    }

    @Test
    void combinesAssistantGuidanceAndConversationContextWhenRegeneratingImage() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "一张打工人工作照片",
                        "https://cdn.example.com/first.png",
                        "FIRST".getBytes(),
                        "first.png",
                        "image/png",
                        null,
                        null))
                .thenReturn(new ImageGenerationResult(
                        "一张更精神的打工人工作照片，高级办公场景",
                        "https://cdn.example.com/second.png",
                        "SECOND".getBytes(),
                        "second.png",
                        "image/png",
                        null,
                        null));
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("明白，刚才生成的画面可能把“打工人”的疲惫感表现得太重了。");
            emitter.emit("如果你希望人物看起来更精神、办公场景更高级，告诉我偏好，我可以重新生成。");
            return null;
        }).when(chatService).streamReply(any(), any());
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        service.handleWechat(new WechatIncomingMessage("user-1", "帮我生成一张打工人正在工作的照片"));
        service.handleWechat(new WechatIncomingMessage("user-1", "生成的人状态好差"));
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "更精神一点，办公场景更高级一点"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/second.png");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(imageGenerationService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).prompt())
                .contains("打工人正在工作的照片")
                .contains("生成的人状态好差")
                .contains("人物看起来更精神、办公场景更高级")
                .contains("更精神一点，办公场景更高级一点");
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
