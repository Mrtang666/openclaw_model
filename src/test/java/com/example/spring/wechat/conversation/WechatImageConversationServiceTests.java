package com.example.spring.wechat.conversation;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.chat.ChatService;
import com.example.spring.weather.service.WeatherService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.intent.WeatherIntentParser;
import com.example.spring.wechat.image.archive.ImageArchiveService;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.image.generation.service.ImageGenerationService;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatImageConversationServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void imageOnlyMessageIsArchivedAndAsksForRequirementWithoutUnderstandingImmediately() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        WechatIncomingMessage imageMessage = imageMessage("msg-1", "user-1", "photo.png");

        String firstReply = service.handle(imageMessage);

        assertThat(firstReply).contains("收到").contains("图片").contains("怎么处理");
        assertThat(imageArchiveService.pendingImages("user-1")).hasSize(1);
        verify(imageUnderstandingService, never()).streamReply(any(), any());
    }

    @Test
    void textAfterPendingImageUsesArchivedImageAndKeepsTheDescriptionForLaterTurns() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        WechatIncomingMessage imageMessage = imageMessage("msg-1", "user-1", "photo.png");
        service.handle(imageMessage);

        doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(1);
            assertThat(analyzedMessage.images().get(0).bytes()).isEqualTo(imageMessage.images().get(0).bytes());
            emitter.emit("我识别到这是一张白色猫咪的照片，背景是蓝天。");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());
        doAnswer(invocation -> {
            ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("它看起来很可爱，也很适合做头像。");
            return null;
        }).when(chatService).streamReply(anyString(), any());

        String firstImageTaskReply = service.handleWechat(new WechatIncomingMessage("user-1", "帮我看看这张图")).text();
        String secondReply = service.handleWechat(new WechatIncomingMessage("user-1", "它适合做头像吗？")).text();

        assertThat(firstImageTaskReply).contains("白色猫咪");
        assertThat(secondReply).isEqualTo("它看起来很可爱，也很适合做头像。");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("白色猫咪")
                        && prompt.contains("蓝天")
                        && prompt.contains("它适合做头像吗？")), any());
    }

    @Test
    void imageCanBeReferencedAgainAfterItWasAlreadyProcessedOnce() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        WechatIncomingMessage imageMessage = imageMessage("msg-repeat", "user-1", "repeat.png");
        service.handle(imageMessage);

        doAnswer(invocation -> {
            ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("第一次识别：这是一张图片。");
            return null;
        }).doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(1);
            assertThat(analyzedMessage.images().get(0).bytes()).isEqualTo(imageMessage.images().get(0).bytes());
            emitter.emit("第二次识别：仍然能拿到这张图片。");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());

        String firstReply = service.handleWechat(new WechatIncomingMessage("user-1", "先描述一下这张图")).text();
        String secondReply = service.handleWechat(new WechatIncomingMessage("user-1", "再分析一下刚才那张图")).text();

        assertThat(firstReply).contains("第一次识别");
        assertThat(secondReply).contains("第二次识别");
        assertThat(imageArchiveService.availableWechatImages("user-1")).hasSize(1);
    }

    @Test
    void textAfterMultipleImagesCanSelectOnlyTheSecondImage() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        WechatIncomingMessage imageMessage = new WechatIncomingMessage(
                "msg-two",
                "user-1",
                "ctx-two",
                "",
                List.of(
                        new WechatIncomingImage(
                                ImageSourceType.WECHAT_ATTACHMENT,
                                "wechat://msg-two/image-1",
                                samplePngBytes(),
                                "image/png",
                                "first.png",
                                null,
                                null,
                                null),
                        new WechatIncomingImage(
                                ImageSourceType.WECHAT_ATTACHMENT,
                                "wechat://msg-two/image-2",
                                samplePngBytes(),
                                "image/png",
                                "second.png",
                                null,
                                null,
                                null)));
        service.handle(imageMessage);

        doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(1);
            assertThat(analyzedMessage.images().get(0).fileName()).isEqualTo("second.png");
            emitter.emit("second image analyzed");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());

        String reply = service.handleWechat(new WechatIncomingMessage("user-1", "please describe the second image")).text();

        assertThat(reply).isEqualTo("second image analyzed");
    }

    @Test
    void processedFiveImagesCanBeReferencedAgainByPreviousFiveImagesPhrase() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        WechatIncomingMessage imageMessage = new WechatIncomingMessage(
                "msg-five",
                "user-1",
                "ctx-five",
                "",
                List.of(
                        image("msg-five", 1),
                        image("msg-five", 2),
                        image("msg-five", 3),
                        image("msg-five", 4),
                        image("msg-five", 5)));
        service.handle(imageMessage);

        doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(5);
            emitter.emit("first pass analyzed five images");
            return null;
        }).doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(5);
            assertThat(analyzedMessage.images()).extracting(WechatIncomingImage::fileName)
                    .containsExactly("image-1.png", "image-2.png", "image-3.png", "image-4.png", "image-5.png");
            emitter.emit("second pass still has five images");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());

        String firstReply = service.handleWechat(new WechatIncomingMessage("user-1", "先描述这些图片")).text();
        String secondReply = service.handleWechat(new WechatIncomingMessage("user-1", "继续分析之前那五张图片")).text();

        assertThat(firstReply).isEqualTo("first pass analyzed five images");
        assertThat(secondReply).isEqualTo("second pass still has five images");
    }

    @Test
    void pdfRequestWithoutPreciseImageWordsUsesLatestUploadBatchInsteadOfAllHistoricalImages() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        java.util.ArrayList<WechatIncomingImage> oldImages = new java.util.ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            oldImages.add(image("old-msg", index));
        }
        imageArchiveService.markUsed(
                "user-1",
                imageArchiveService.archiveUserImages("user-1", "old-msg", oldImages));
        imageArchiveService.markUsed(
                "user-1",
                imageArchiveService.archiveUserImages("user-1", "new-msg", List.of(image("new-msg", 1), image("new-msg", 2))));

        doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(2);
            assertThat(analyzedMessage.images())
                    .extracting(WechatIncomingImage::sourceReference)
                    .containsExactly("wechat://new-msg/image-1", "wechat://new-msg/image-2");
            emitter.emit("latest two images selected");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());

        String reply = service.handleWechat(new WechatIncomingMessage("user-1", "please compare and make a PDF")).text();

        assertThat(reply).isEqualTo("latest two images selected");
    }

    @Test
    void textImageReferenceCanUseModelSemanticsBeforeFallingBackToLatestImage() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                new WeatherIntentParser(),
                imageArchiveService);

        imageArchiveService.markUsed(
                "user-1",
                imageArchiveService.archiveUserImages("user-1", "msg-blue", List.of(image("msg-blue", 1, "blue-background.png"))));
        imageArchiveService.markUsed(
                "user-1",
                imageArchiveService.archiveUserImages("user-1", "msg-red", List.of(image("msg-red", 1, "red-background.png"))));
        imageArchiveService.markUsed(
                "user-1",
                imageArchiveService.archiveUserImages("user-1", "msg-green", List.of(image("msg-green", 1, "green-background.png"))));
        when(chatService.reply(anyString())).thenReturn("""
                {
                  "selected_image_indexes": [2],
                  "needs_clarification": false,
                  "clarification_question": "",
                  "reason": "用户说红色背景，应选择 red-background.png。"
                }
                """);
        doAnswer(invocation -> {
            WechatIncomingMessage analyzedMessage = invocation.getArgument(0);
            ReplyEmitter emitter = invocation.getArgument(1);
            assertThat(analyzedMessage.images()).hasSize(1);
            assertThat(analyzedMessage.images().get(0).fileName()).isEqualTo("red-background.png");
            emitter.emit("red image selected");
            return null;
        }).when(imageUnderstandingService).streamReply(any(), any());

        String reply = service.handleWechat(new WechatIncomingMessage("user-1", "把红色背景的图片做成 PDF")).text();

        assertThat(reply).isEqualTo("red image selected");
        verify(chatService).reply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("图片引用语义解析器")
                        && prompt.contains("red-background.png")
                        && prompt.contains("green-background.png")));
    }

    @Test
    void imageRefinementUsesMostRecentUserImageDescriptionInsteadOfOlderGeneratedImage() throws IOException {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageUnderstandingService imageUnderstandingService = mock(ImageUnderstandingService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        ImageArchiveService imageArchiveService = new ImageArchiveService(null, tempDir, 5);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                imageUnderstandingService,
                imageGenerationService,
                new WeatherIntentParser(),
                imageArchiveService);

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
        }).when(imageUnderstandingService).streamReply(any(), any());

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

    private WechatIncomingImage image(String messageId, int index) throws IOException {
        return image(messageId, index, "image-" + index + ".png");
    }

    private WechatIncomingImage image(String messageId, int index, String fileName) throws IOException {
        return new WechatIncomingImage(
                ImageSourceType.WECHAT_ATTACHMENT,
                "wechat://" + messageId + "/image-" + index,
                samplePngBytes(),
                "image/png",
                fileName,
                null,
                null,
                null);
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
