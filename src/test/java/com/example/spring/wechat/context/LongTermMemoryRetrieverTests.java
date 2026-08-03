package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryRetrieverTests {

    @Test
    void searchesLongTermMemoryWithMemoryTypeTag() {
        KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
        when(searchService.search("session", "上下文优化", 5, "memory_type:long_term_memory"))
                .thenReturn(List.of(result("用户偏好先看设计")));

        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(searchService);

        assertThat(retriever.longTermMemories("session", "上下文优化", 5))
                .contains("用户偏好先看设计");
    }

    @Test
    void searchesConversationTopicsWithMemoryTypeTag() {
        KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
        when(searchService.search("session", "天气", 5, "memory_type:conversation_topic"))
                .thenReturn(List.of(result("历史主题：OpenClaw 上下文机制")));

        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(searchService);

        assertThat(retriever.conversationTopics("session", "天气", 5))
                .contains("历史主题：OpenClaw 上下文机制");
    }

    private KnowledgeSearchResult result(String content) {
        return new KnowledgeSearchResult(1L, "title", 0, content, "memory_graph", "memory://1", 0.9);
    }
}
