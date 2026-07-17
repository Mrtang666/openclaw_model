package com.example.spring.wechat.bot;

import com.example.spring.agent.AgentService;
import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.wechat.client.WechatClient;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.wechat.client.WechatLoginInfo;
import com.example.spring.wechat.conversation.WechatConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
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

    private static final String THINKING_MESSAGE = "[自动回复]正在生成中，请耐心等待哦~";

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
        assertThat(new ArrayList<>(client.sentTexts)).containsExactly(
                THINKING_MESSAGE,
                "南京天气结果");
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
    void keepsProcessingLaterMessagesWhileEarlierOneIsStillRunning() throws Exception {
        FakeWechatClient client = new FakeWechatClient(4);
        AgentService agentService = mock(AgentService.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        doAnswer(invocation -> {
            String input = invocation.getArgument(0);
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            if ("第一条".equals(input)) {
                firstStarted.countDown();
                emitter.emit("第一条回复");
                allowFirstToFinish.await(3, TimeUnit.SECONDS);
            } else if ("第二条".equals(input)) {
                emitter.emit("第二条回复");
            }
            return null;
        }).when(agentService).handleStreaming(any(), any());
        WechatBotService service = new WechatBotService(() -> client, agentService);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "第一条")));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "第二条")));

        assertThat(firstStarted.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(Collections.frequency(new ArrayList<>(client.sentTexts), THINKING_MESSAGE)).isEqualTo(2);

        allowFirstToFinish.countDown();
        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(new ArrayList<>(client.sentTexts))
                .containsSequence(THINKING_MESSAGE, THINKING_MESSAGE, "第一条回复", "第二条回复");
        service.stop();
    }

    @Test
    void sendsGeneratedImageBackToWechat() throws Exception {
        FakeWechatClient client = new FakeWechatClient(2);
        WechatConversationService conversationService = mock(WechatConversationService.class);
        when(conversationService.handleWechat(any())).thenReturn(WechatReply.textAndImage(
                "我已经帮你生成好了，图片如下：",
                new ImageGenerationResult(
                        "帮我画一只赛博朋克风格的橘猫",
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
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "帮我画一只赛博朋克风格的橘猫")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(new ArrayList<>(client.sentTexts)).containsExactly(THINKING_MESSAGE);
        assertThat(client.sentImages).hasSize(1);
        assertThat(client.sentImages.peek().fileName()).isEqualTo("generated.png");
        assertThat(client.sentImages.peek().caption()).isEqualTo("我已经帮你生成好了，图片如下：");
        service.stop();
    }

    private static final class FakeWechatClient implements WechatClient {
        private final CompletableFuture<WechatLoginInfo> loginFuture = new CompletableFuture<>();
        private final Queue<List<WechatIncomingMessage>> updates = new ConcurrentLinkedQueue<>();
        private final CountDownLatch sentLatch;
        private final Queue<String> sentTexts = new ConcurrentLinkedQueue<>();
        private final Queue<SentImage> sentImages = new ConcurrentLinkedQueue<>();
        private volatile String sentToUserId;
        private volatile boolean closed;

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
        public List<WechatIncomingMessage> getUpdates() throws IOException {
            List<WechatIncomingMessage> messages = updates.poll();
            if (messages == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
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
        public void close() {
            closed = true;
            loginFuture.cancel(true);
        }

        private record SentImage(byte[] imageBytes, String fileName, String caption) {
        }
    }
}
