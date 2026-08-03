package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryGraphMaintenanceService {

    private final WechatContextProperties properties;
    private final MemoryGraphRepository repository;
    private final KnowledgeIngestionService ingestionService;
    private final LongTermMemoryExtractor longTermMemoryExtractor;

    public MemoryGraphMaintenanceService(
            WechatContextProperties properties,
            MemoryGraphRepository repository,
            KnowledgeIngestionService ingestionService,
            LongTermMemoryExtractor longTermMemoryExtractor) {
        this.properties = properties;
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
    }

    public void ingestLongTermMemories(String sessionKey, Long conversationId, String transcript) {
        if (properties == null || !properties.longTermMemoryIngestionEnabled()
                || repository == null || ingestionService == null || longTermMemoryExtractor == null) {
            return;
        }
        List<MemoryGraphNodeDraft> drafts = longTermMemoryExtractor.extract(sessionKey, conversationId, transcript);
        for (MemoryGraphNodeDraft draft : drafts == null ? List.<MemoryGraphNodeDraft>of() : drafts) {
            MemoryGraphNode node = repository.createNode(draft);
            ingestionService.add(
                    sessionKey,
                    draft.title(),
                    draft.content(),
                    "memory_graph",
                    "memory://long_term_memory/" + node.id(),
                    "memory_type:long_term_memory");
        }
    }
}
