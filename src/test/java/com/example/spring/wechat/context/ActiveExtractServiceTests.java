package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveExtractServiceTests {

    @Test
    void extractsCurrentTopicOnly() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("活摘抽取器"))).thenReturn("用户已确认 Memory Graph 使用方案 C。");
        ActiveExtractService service = new ActiveExtractService(chatService);

        String extract = service.extract("Memory Graph", "摘要里有 Memory Graph，也有杭州天气。");

        assertThat(extract).contains("Memory Graph").doesNotContain("杭州天气");
    }
}
