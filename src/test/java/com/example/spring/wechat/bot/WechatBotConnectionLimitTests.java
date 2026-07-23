package com.example.spring.wechat.bot;

import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.bot.concurrency.WechatConcurrencyProperties;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionProperties;
import com.example.spring.wechat.conversation.WechatConversationService;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatLoginInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WechatBotConnectionLimitTests {

    @Test
    void enforcesPendingLoginLimit() {
        WechatBotService service = service(10, 1);
        try {
            service.addConnection();
            assertThatThrownBy(service::addConnection)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("待扫码登录数已达到上限 1");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void enforcesTotalConnectionLimit() {
        WechatBotService service = service(2, 3);
        try {
            service.addConnection();
            service.addConnection();
            assertThatThrownBy(service::addConnection)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("最大连接数 2");
        } finally {
            service.shutdown();
        }
    }

    private WechatBotService service(int maxConnections, int maxPending) {
        ClawBotConnectionProperties connectionProperties = new ClawBotConnectionProperties();
        connectionProperties.setMaxConnections(maxConnections);
        connectionProperties.setMaxPendingLogins(maxPending);
        WechatConcurrencyProperties concurrencyProperties = new WechatConcurrencyProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<WechatConversationService> provider = mock(ObjectProvider.class);
        return new WechatBotService(
                PendingClient::new,
                provider,
                null,
                null,
                connectionProperties,
                concurrencyProperties);
    }

    private static final class PendingClient implements WechatClient {
        private final CompletableFuture<WechatLoginInfo> login = new CompletableFuture<>();

        @Override public String executeLogin() { return "https://liteapp.weixin.qq.com/q/test"; }
        @Override public CompletableFuture<WechatLoginInfo> loginFuture() { return login; }
        @Override public List<WechatIncomingMessage> getUpdates() { return List.of(); }
        @Override public void sendText(String toUserId, String text) throws IOException { }
        @Override public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) { }
        @Override public void close() { login.cancel(true); }
    }
}
