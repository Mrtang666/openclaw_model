package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryExtractorTests {

    @Test
    void extractsStableMemoriesAndFiltersTemporarySensitiveItems() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("长期记忆抽取器"))).thenReturn("""
                [
                  {"type":"LONG_TERM_MEMORY","title":"用户偏好","content":"用户偏好先看完整设计再实现","confidence":0.92},
                  {"type":"LONG_TERM_MEMORY","title":"临时提醒","content":"明天提醒用户喝水","confidence":0.95},
                  {"type":"LONG_TERM_MEMORY","title":"密钥","content":"用户 API key 是 abc","confidence":0.95}
                ]
                """);

        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(chatService);

        List<MemoryGraphNodeDraft> drafts = extractor.extract(
                "session",
                1L,
                "用户说以后先写设计，再实现。明天提醒我喝水。API key 是 abc。");

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).content()).contains("先看完整设计");
        assertThat(drafts.get(0).nodeType()).isEqualTo(MemoryNodeType.LONG_TERM_MEMORY);
        assertThat(drafts.get(0).tags()).isEqualTo("memory_type:long_term_memory");
    }
}
