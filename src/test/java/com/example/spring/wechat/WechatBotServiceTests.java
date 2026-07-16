package com.example.spring.wechat;

import com.example.spring.agent.AgentService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    void forwardsIncomingTextToAgentAndRepliesToWechat() throws Exception {
        FakeWechatClient client = new FakeWechatClient();
        AgentService agentService = mock(AgentService.class);
        when(agentService.handle("weather 南京")).thenReturn("南京天气结果");
        WechatBotService service = new WechatBotService(() -> client, agentService);

        service.start();
        client.loginFuture.complete(new WechatLoginInfo("bot-1"));
        client.updates.add(List.of(new WechatIncomingMessage("user@im.wechat", "weather 南京")));

        assertThat(client.sentLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(client.sentToUserId).isEqualTo("user@im.wechat");
        assertThat(client.sentText).isEqualTo("南京天气结果");
        assertThat(service.status().state()).isEqualTo(WechatBotState.RUNNING);
        service.stop();
    }

    private static final class FakeWechatClient implements WechatClient {
        private final CompletableFuture<WechatLoginInfo> loginFuture = new CompletableFuture<>();
        private final Queue<List<WechatIncomingMessage>> updates = new ConcurrentLinkedQueue<>();
        private final CountDownLatch sentLatch = new CountDownLatch(1);
        private volatile String sentToUserId;
        private volatile String sentText;
        private volatile boolean closed;

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
            this.sentText = text;
            sentLatch.countDown();
        }

        @Override
        public void close() {
            closed = true;
            loginFuture.cancel(true);
        }
    }
}
