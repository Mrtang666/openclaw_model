package com.example.spring.wechat.memory;

import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.memory.fallback.InMemoryWechatMemoryFallback;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryWechatMemoryFallbackTests {

    @Test
    void startNewConversationDropsSessionButKeepsPreferences() {
        InMemoryWechatMemoryFallback fallback = new InMemoryWechatMemoryFallback(
                new WechatMemoryProperties(60, 30, 6, 20));
        Instant now = Instant.parse("2026-07-27T00:00:00Z");

        fallback.saveExplicitPreference("wx-fallback", "voice", "{\"name\":\"Cherry\"}", "test", now);
        long firstConversationId = fallback.open("wx-fallback", now).conversationId();

        fallback.startNewConversation("wx-fallback", now.plusSeconds(1));

        long secondConversationId = fallback.open("wx-fallback", now.plusSeconds(2)).conversationId();
        assertThat(secondConversationId).isNotEqualTo(firstConversationId);
        assertThat(fallback.explicitPreference("wx-fallback", "voice"))
                .contains("{\"name\":\"Cherry\"}");
    }
}
