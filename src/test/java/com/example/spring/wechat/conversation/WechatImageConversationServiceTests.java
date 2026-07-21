package com.example.spring.wechat.conversation;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.chat.ChatService;
import com.example.spring.weather.service.WeatherService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.intent.WeatherIntentParser;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.image.generation.service.ImageGenerationService;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingMessage;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatImageConversationServiceTests {

    @Test
    void describesImageFirstAndKeepsTheDescriptionForLaterTurns() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser());

        WechatIncomingMessage imageMessage = imageMessage("msg-1", "user-1", "photo.png");

        doAnswer(invocation -> {
            ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("我识别到这是一张白色猫咪的照片，背景是蓝天。");
            return null;
        }).when(imageUnderstandingService).streamReply(eq(imageMessage), any());
        doAnswer(invocation -> {
            ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("它看起来很可爱，也很适合做头像。");
            return null;
        }).when(chatService).streamReply(anyString(), any());

        String firstReply = service.handle(imageMessage);
        String secondReply = service.handle("user-1", "它适合做头像吗？");

        assertThat(firstReply).contains("白色猫咪");
        assertThat(secondReply).isEqualTo("它看起来很可爱，也很适合做头像。");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("白色猫咪")
                        && prompt.contains("蓝天")
                        && prompt.contains("它适合做头像吗？")), any());
    }

    @Test
    void imageRefinementUsesMostRecentUserImageDescriptionInsteadOfOlderGeneratedImage() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                imageGenerationService,
                new WeatherIntentParser());

        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "赛博朋克风格的橘猫",
                        "https://cdn.example.com/cat.png",
                        "CAT".getBytes(),
                        "cat.png",
                        "image/png",
                        null,
                        null))
                .thenReturn(new ImageGenerationResult(
                        "白色狗狗，红色围巾，夜景背景",
                        "https://cdn.example.com/dog-night.png",
                        "DOG".getBytes(),
                        "dog-night.png",
                        "image/png",
                        null,
                        null));

        WechatIncomingMessage imageMessage = imageMessage("msg-2", "user-1", "dog.png");

        doAnswer(invocation -> {
            ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("我识别到这是一张白色狗狗的照片，狗狗戴着红色围巾，背景是草地。");
            return null;
        }).when(imageUnderstandingService).streamReply(eq(imageMessage), any());

        service.handleWechat(new WechatIncomingMessage("user-1", "帮我画一只赛博朋克风格的橘猫"));
        service.handleWechat(imageMessage);
        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "把背景换成夜景"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/dog-night.png");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(imageGenerationService, times(2)).generate(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(1).prompt())
                .contains("白色狗狗")
                .contains("红色围巾")
                .contains("把背景换成夜景")
                .doesNotContain("赛博朋克风格的橘猫");
    }

    private WechatIncomingMessage imageMessage(String messageId, String userId, String fileName) throws IOException {
        return new WechatIncomingMessage(
                messageId,
                userId,
                "ctx-" + messageId,
                "",
                List.of(new WechatIncomingImage(
                        ImageSourceType.WECHAT_ATTACHMENT,
                        "wechat://" + messageId + "/image-1",
                        samplePngBytes(),
                        "image/png",
                        fileName,
                        null,
                        null,
                        null)));
    }

    private byte[] samplePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(0, 1, Color.GREEN.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
