package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryGraphMaintenanceServiceTests {

    @Test
    void writesExtractedLongTermMemoriesToGraphAndRag() {
        MemoryGraphRepository repository = mock(MemoryGraphRepository.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        LongTermMemoryExtractor extractor = mock(LongTermMemoryExtractor.class);
        MemoryGraphNodeDraft draft = new MemoryGraphNodeDraft(
                "session",
                7L,
                MemoryNodeType.LONG_TERM_MEMORY,
                "preference",
                "用户偏好",
                "用户偏好先看设计",
                "用户偏好先看设计",
                0.9,
                0,
                0.9,
                null,
                null,
                "conversation",
                "conversation://7",
                "memory_type:long_term_memory",
                null);
        when(extractor.extract("session", 7L, "transcript")).thenReturn(List.of(draft));
        when(repository.createNode(draft)).thenReturn(new MemoryGraphNode(
                99L, "session", 7L, MemoryNodeType.LONG_TERM_MEMORY, "preference", "用户偏好",
                "用户偏好先看设计", "用户偏好先看设计", 0.9, 0, 0.9,
                null, null, "conversation", "conversation://7", "memory_type:long_term_memory",
                null, null, null, false));
        MemoryGraphMaintenanceService service = new MemoryGraphMaintenanceService(
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8),
                repository,
                ingestionService,
                extractor);

        service.ingestLongTermMemories("session", 7L, "transcript");

        verify(repository).createNode(draft);
        verify(ingestionService).add(
                "session",
                "用户偏好",
                "用户偏好先看设计",
                "memory_graph",
                "memory://long_term_memory/99",
                "memory_type:long_term_memory");
    }

    @Test
    void maintainsSlidingSummaryTopicAndActiveExtractNodes() {
        MemoryGraphRepository repository = mock(MemoryGraphRepository.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        LongTermMemoryExtractor longTermExtractor = mock(LongTermMemoryExtractor.class);
        SlidingWindowSummaryService summaryService = mock(SlidingWindowSummaryService.class);
        ActiveExtractService activeExtractService = mock(ActiveExtractService.class);
        WechatConversationMemory memory = WechatConversationMemory.empty(10);
        memory.record("第1轮用户", "第1轮助手");
        memory.record("第2轮用户", "第2轮助手");
        memory.record("第3轮用户", "第3轮助手");
        memory.record("第4轮用户", "第4轮助手");
        memory.record("第5轮用户", "第5轮助手");
        memory.record("第6轮用户", "第6轮助手");
        when(summaryService.summarizeOutsideRecentWindow(memory, 5))
                .thenReturn("旧对话摘要：用户正在优化上下文机制。");
        when(activeExtractService.extract("上下文机制", "旧对话摘要：用户正在优化上下文机制。"))
                .thenReturn("活摘：当前主题是 Memory Graph 上下文优化。");
        when(repository.createNode(any())).thenAnswer(invocation -> {
            MemoryGraphNodeDraft draft = invocation.getArgument(0);
            return new MemoryGraphNode(
                    switch (draft.nodeType()) {
                        case CONVERSATION_SUMMARY -> 101L;
                        case CONVERSATION_TOPIC -> 102L;
                        case ACTIVE_EXTRACT -> 103L;
                        default -> 199L;
                    },
                    draft.sessionKey(),
                    draft.conversationId(),
                    draft.nodeType(),
                    draft.topicKey(),
                    draft.title(),
                    draft.content(),
                    draft.summary(),
                    draft.importanceScore(),
                    draft.relevanceScore(),
                    draft.confidenceScore(),
                    draft.sourceMessageStartId(),
                    draft.sourceMessageEndId(),
                    draft.sourceType(),
                    draft.sourceRef(),
                    draft.tags(),
                    null,
                    null,
                    draft.expiresAt(),
                    false);
        });
        MemoryGraphMaintenanceService service = new MemoryGraphMaintenanceService(
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8),
                repository,
                ingestionService,
                longTermExtractor,
                summaryService,
                activeExtractService);

        service.maintainConversationWindow("session", 7L, memory, "上下文机制");

        ArgumentCaptor<MemoryGraphNodeDraft> draftCaptor = ArgumentCaptor.forClass(MemoryGraphNodeDraft.class);
        verify(repository, org.mockito.Mockito.times(3)).createNode(draftCaptor.capture());
        assertThat(draftCaptor.getAllValues())
                .extracting(MemoryGraphNodeDraft::nodeType)
                .containsExactly(
                        MemoryNodeType.CONVERSATION_SUMMARY,
                        MemoryNodeType.CONVERSATION_TOPIC,
                        MemoryNodeType.ACTIVE_EXTRACT);
        verify(ingestionService).add(
                "session",
                "历史主题：上下文机制",
                "旧对话摘要：用户正在优化上下文机制。",
                "memory_graph",
                "memory://conversation_topic/102",
                "memory_type:conversation_topic");
        verify(ingestionService).add(
                "session",
                "活摘：上下文机制",
                "活摘：当前主题是 Memory Graph 上下文优化。",
                "memory_graph",
                "memory://active_extract/103",
                "memory_type:active_extract");
    }

    @Test
    void reusesExistingConversationSummaryWhenRecentMemoryWindowIsSmall() {
        MemoryGraphRepository repository = mock(MemoryGraphRepository.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        LongTermMemoryExtractor longTermExtractor = mock(LongTermMemoryExtractor.class);
        SlidingWindowSummaryService summaryService = mock(SlidingWindowSummaryService.class);
        ActiveExtractService activeExtractService = mock(ActiveExtractService.class);
        WechatConversationMemory memory = WechatConversationMemory.empty(
                5,
                "已有摘要：用户正在优化 Memory Graph 上下文机制。");
        memory.record("最近问题", "最近回答");
        when(activeExtractService.extract("Memory Graph 上下文", "已有摘要：用户正在优化 Memory Graph 上下文机制。"))
                .thenReturn("活摘：保留 Memory Graph 上下文优化决策。");
        when(repository.createNode(any())).thenAnswer(invocation -> {
            MemoryGraphNodeDraft draft = invocation.getArgument(0);
            return new MemoryGraphNode(
                    draft.nodeType() == MemoryNodeType.ACTIVE_EXTRACT ? 203L : 201L,
                    draft.sessionKey(),
                    draft.conversationId(),
                    draft.nodeType(),
                    draft.topicKey(),
                    draft.title(),
                    draft.content(),
                    draft.summary(),
                    draft.importanceScore(),
                    draft.relevanceScore(),
                    draft.confidenceScore(),
                    draft.sourceMessageStartId(),
                    draft.sourceMessageEndId(),
                    draft.sourceType(),
                    draft.sourceRef(),
                    draft.tags(),
                    null,
                    null,
                    draft.expiresAt(),
                    false);
        });
        MemoryGraphMaintenanceService service = new MemoryGraphMaintenanceService(
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8),
                repository,
                ingestionService,
                longTermExtractor,
                summaryService,
                activeExtractService);

        service.maintainConversationWindow("session", 8L, memory, "Memory Graph 上下文");

        ArgumentCaptor<MemoryGraphNodeDraft> draftCaptor = ArgumentCaptor.forClass(MemoryGraphNodeDraft.class);
        verify(repository, org.mockito.Mockito.times(3)).createNode(draftCaptor.capture());
        assertThat(draftCaptor.getAllValues().get(0).content())
                .isEqualTo("已有摘要：用户正在优化 Memory Graph 上下文机制。");
    }
}
