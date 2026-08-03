package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
