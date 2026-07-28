package com.example.spring.wechat.conversation.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WechatToolRequestUserIdTests {

    @Test
    void extractsStableWechatUserIdFromConversationKey() {
        WechatToolRequest request = new WechatToolRequest(
                "clawbot:connection-1:user@im.wechat", "点外卖", Map.of(), "", null, null);

        assertThat(request.userId()).isEqualTo("user@im.wechat");
    }

    @Test
    void keepsPlainSessionKeyAsFallback() {
        WechatToolRequest request = new WechatToolRequest(
                "plain-user", "点外卖", Map.of(), "", null, null);

        assertThat(request.userId()).isEqualTo("plain-user");
    }
}
