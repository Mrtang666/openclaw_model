package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlidingWindowSummaryServiceTests {

    @Test
    void summarizesTurnsOutsideStrongWindow() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("滑动窗口摘要器"))).thenReturn("第 1-6 轮摘要");
        WechatConversationMemory memory = WechatConversationMemory.empty(20);
        for (int index = 1; index <= 11; index++) {
            memory.record("user-" + index, "assistant-" + index);
        }

        SlidingWindowSummaryService service = new SlidingWindowSummaryService(chatService);

        String summary = service.summarizeOutsideRecentWindow(memory, 5);

        assertThat(summary).isEqualTo("第 1-6 轮摘要");
    }
}
