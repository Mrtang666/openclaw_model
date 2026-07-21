package com.example.spring.wechat.bot;

import com.example.spring.agent.AgentService;
import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.conversation.WechatConversationService;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatLoginInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatBotServiceTests {

    @Test
    void startsClientAndReportsWaitingForScan() {
        FakeWechatClient client = new FakeWechatClient();
        WechatBotService service = new WechatBotService(() -> client, mock(AgentService.class));

        WechatStartResult result = service.start();

        assertThat(result.started()).isTrue();
        assertThat(result.qrCodeContent()).isEqualTo("QR-CONTENT");
        assertThat(service.status().state()).isEqualTo(WechatBotState.WAITING_FOR_SCAN);
        service.stop();
    }

    @Test
    void forwardsIncomingTextToAgentAndSendsFinalReplyOnce() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        AgentService agentService = mock(AgentService.class);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("南京");
            emitter.emit("天气结果");
            return null;
        }).when(agentService).handleStreaming(eq("你是谁"), any());
        WechatBotService service = new WechatBotService(() -> client, agentService);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "你是谁")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentToUserId).isEqualTo("user@im.wechat");
        assertThat(new ArrayList<>(client.sentTexts)).hasSize(2);
        assertThat(new ArrayList<>(client.sentTexts).get(1)).isEqualTo("南京天气结果");
        assertThat(service.status().state()).isEqualTo(WechatBotState.RUNNING);
        service.stop();
    }

    @Test
    void splitsLongWechatRepliesIntoSafeChunks() {
        String longReply = "长文本".repeat(500);

        List<String> chunks = WechatBotService.splitForWechat(longReply);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 800);
        assertThat(String.join("", chunks)).isEqualTo(longReply);
    }

    @Test
    void sendsGeneratedImageBackToWechat() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.textAndImage(
                "图片如下：",
                new ImageGenerationResult(
                        "赛博朋克风格的橘猫",
                        "https://cdn.example.com/generated.png",
                        "PNG".getBytes(),
                        "generated.png",
                        "image/png",
                        null,
                        null)));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "帮我画一只橘猫")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentImages).hasSize(1);
        assertThat(client.sentImages.peek().fileName()).isEqualTo("generated.png");
        assertThat(client.sentImages.peek().caption()).isEqualTo("图片如下：");
        service.stop();
    }

    @Test
    void sendsOptimizedPromptTextBeforeGeneratedImage() throws Exception {
        FakeWechatClient client = new FakeWechatClient(3);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.textsAndImage(
                List.of("优化后的图片提示词：\n一只小猫坐在沙发上，暖色自然光。"),
                "图片如下：",
                new ImageGenerationResult(
                        "一只小猫坐在沙发上，暖色自然光。",
                        "https://cdn.example.com/cat.png",
                        "PNG".getBytes(),
                        "cat.png",
                        "image/png",
                        null,
                        null)));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "先优化提示词再生成图片")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(new ArrayList<>(client.sentTexts).get(1))
                .isEqualTo("优化后的图片提示词：\n一只小猫坐在沙发上，暖色自然光。");
        assertThat(client.sentImages).hasSize(1);
        service.stop();
    }

    @Test
    void sendsVoiceReplyPartsAsNativeWechatVoiceBubbles() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.ordered(List.of(
                WechatReply.Part.voice(new WechatReply.Voice(
                        "VOICE".getBytes(),
                        "reply-1.silk",
                        2200,
                        16000,
                        6,
                        16,
                        "你好，我会用语音回复你。")))));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "请用语音回复我")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentVoices).hasSize(1);
        assertThat(client.sentVoices.peek().fileName()).isEqualTo("reply-1.silk");
        assertThat(client.sentVoices.peek().encodeType()).isEqualTo(6);
        service.stop();
    }

    @Test
    void sendsNonSilkVoiceReplyAsAudioFileSoUserCanReceiveIt() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.ordered(List.of(
                WechatReply.Part.voice(new WechatReply.Voice(
                        "MP3".getBytes(),
                        "reply-1.mp3",
                        2200,
                        16000,
                        null,
                        null,
                        "语音已经生成。")))));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "用语音回复")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentFiles).hasSize(1);
        assertThat(client.sentFiles.peek().fileName()).isEqualTo("reply-1.mp3");
        service.stop();
    }

    @Test
    void retriesLaterVoiceFilePartWhenWechatTemporarilyRejectsLongAudioReply() throws Exception {
        FakeWechatClient client = new FakeWechatClient(6);
        client.failFileSendsBeforeSuccess("reply-5.mp3", 1);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.ordered(List.of(
                voiceFilePart(1),
                voiceFilePart(2),
                voiceFilePart(3),
                voiceFilePart(4),
                voiceFilePart(5))));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "read a long story by voice")));

        assertThat(client.sentLatch.await(8, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentFiles).hasSize(5);
        assertThat(new ArrayList<>(client.sentFiles).stream().map(SentFile::fileName))
                .containsExactly("reply-1.mp3", "reply-2.mp3", "reply-3.mp3", "reply-4.mp3", "reply-5.mp3");
        assertThat(client.fileSendAttempts("reply-5.mp3")).isEqualTo(2);
        service.stop();
    }

    @Test
    void sendsDocumentReplyPartsAsWechatFiles() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.ordered(List.of(
                WechatReply.Part.file(new WechatReply.FileAttachment(
                        "DOCX".getBytes(),
                        "汇报.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "文档已生成，请查收")))));
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(conversationService);
        WechatBotService service = new WechatBotService(() -> client, provider);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "生成文档")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentFiles).hasSize(1);
        assertThat(client.sentFiles.peek().fileName()).isEqualTo("汇报.docx");
        assertThat(client.sentFiles.peek().caption()).isEqualTo("文档已生成，请查收");
        service.stop();
    }

    private static WechatReply.Part voiceFilePart(int index) {
        return WechatReply.Part.voice(new WechatReply.Voice(
                ("MP3-" + index).getBytes(),
                "reply-" + index + ".mp3",
                2200,
                16000,
                null,
                null,
                "voice segment " + index));
    }

    private record SentImage(byte[] imageBytes, String fileName, String caption) {
    }

    private record SentVoice(
            byte[] voiceBytes,
            String fileName,
            Integer playTimeMs,
            Integer sampleRate,
            Integer encodeType,
            Integer bitsPerSample,
            String transcriptText) {
    }

    private record SentFile(byte[] fileBytes, String fileName, String caption) {
    }

    private static final class FakeWechatClient implements WechatClient {
        private final CompletableFuture<WechatLoginInfo> loginFuture = new CompletableFuture<>();
        private final Queue<List<WechatIncomingMessage>> updates = new ConcurrentLinkedQueue<>();
        private final CountDownLatch sentLatch;
        private final Queue<String> sentTexts = new ConcurrentLinkedQueue<>();
        private final Queue<SentImage> sentImages = new ConcurrentLinkedQueue<>();
        private final Queue<SentVoice> sentVoices = new ConcurrentLinkedQueue<>();
        private final Queue<SentFile> sentFiles = new ConcurrentLinkedQueue<>();
        private final Map<String, Integer> fileFailuresBeforeSuccess = new ConcurrentHashMap<>();
        private final Map<String, Integer> fileSendAttempts = new ConcurrentHashMap<>();
        private volatile String sentToUserId;

        private FakeWechatClient() {
            this(1);
        }

        private FakeWechatClient(int expectedSends) {
            this.sentLatch = new CountDownLatch(expectedSends);
        }

        @Override
        public String executeLogin() {
            return "QR-CONTENT";
        }

        @Override
        public CompletableFuture<WechatLoginInfo> loginFuture() {
            return loginFuture;
        }

        @Override
        public List<WechatIncomingMessage> getUpdates() {
            List<WechatIncomingMessage> messages = updates.poll();
            if (messages == null) {
                return List.of();
            }
            return messages;
        }

        @Override
        public void sendText(String toUserId, String text) {
            this.sentToUserId = toUserId;
            this.sentTexts.add(text);
            sentLatch.countDown();
        }

        @Override
        public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) {
            this.sentToUserId = toUserId;
            this.sentImages.add(new SentImage(imageBytes, fileName, caption));
            sentLatch.countDown();
        }

        @Override
        public void sendVoice(
                String toUserId,
                byte[] voiceBytes,
                String fileName,
                Integer playTimeMs,
                Integer sampleRate,
                Integer encodeType,
                Integer bitsPerSample,
                String transcriptText) {
            this.sentToUserId = toUserId;
            this.sentVoices.add(new SentVoice(voiceBytes, fileName, playTimeMs, sampleRate, encodeType, bitsPerSample, transcriptText));
            sentLatch.countDown();
        }

        @Override
        public void sendFile(String toUserId, byte[] fileBytes, String fileName, String caption) throws IOException {
            int attempts = fileSendAttempts.merge(fileName, 1, Integer::sum);
            int failuresBeforeSuccess = fileFailuresBeforeSuccess.getOrDefault(fileName, 0);
            if (attempts <= failuresBeforeSuccess) {
                throw new IOException("temporary file send failure");
            }
            this.sentToUserId = toUserId;
            this.sentFiles.add(new SentFile(fileBytes, fileName, caption));
            sentLatch.countDown();
        }

        private void failFileSendsBeforeSuccess(String fileName, int failuresBeforeSuccess) {
            this.fileFailuresBeforeSuccess.put(fileName, failuresBeforeSuccess);
        }

        private int fileSendAttempts(String fileName) {
            return this.fileSendAttempts.getOrDefault(fileName, 0);
        }

        @Override
        public void close() {
            loginFuture.cancel(true);
        }
    }
}
