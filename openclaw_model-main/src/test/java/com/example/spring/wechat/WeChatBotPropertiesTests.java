package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "ilink.bot.enabled=false")
class WeChatBotPropertiesTests {
    @Autowired
    private WeChatBotProperties properties;

    @Test
    void bindsChineseFixedReplyWithoutMojibake() {
        assertThat(properties.getFixedReply()).isEqualTo("你好，我已收到你的消息。");
    }
}
