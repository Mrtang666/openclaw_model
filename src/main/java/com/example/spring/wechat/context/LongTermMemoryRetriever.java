package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LongTermMemoryRetriever {

    static final String LONG_TERM_TAG = "memory_type:long_term_memory";
    static final String TOPIC_TAG = "memory_type:conversation_topic";

    private final KnowledgeSearchService searchService;

    public LongTermMemoryRetriever(KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    public List<String> longTermMemories(String sessionKey, String query, int limit) {
        return search(sessionKey, query, limit, LONG_TERM_TAG);
    }

    public List<String> conversationTopics(String sessionKey, String query, int limit) {
        return search(sessionKey, query, limit, TOPIC_TAG);
    }

    private List<String> search(String sessionKey, String query, int limit, String tags) {
        if (searchService == null || query == null || query.isBlank()) {
            return List.of();
        }
        return searchService.search(sessionKey, query, limit, tags).stream()
                .map(KnowledgeSearchResult::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }
}
