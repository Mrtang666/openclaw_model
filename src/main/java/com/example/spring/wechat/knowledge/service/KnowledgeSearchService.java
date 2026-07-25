package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.vector.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索服务。
 */
@Service
public class KnowledgeSearchService {

    private final KnowledgeEmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;
    private final KnowledgeQueryPlanner queryPlanner;

    @Autowired
    public KnowledgeSearchService(
            KnowledgeEmbeddingService embeddingService,
            VectorStore vectorStore,
            KnowledgeProperties properties,
            KnowledgeQueryPlanner queryPlanner) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.queryPlanner = queryPlanner;
    }

    public KnowledgeSearchService(
            KnowledgeEmbeddingService embeddingService,
            VectorStore vectorStore,
            KnowledgeProperties properties) {
        this(embeddingService, vectorStore, properties, new KnowledgeQueryPlanner(null));
    }

    public List<KnowledgeSearchResult> search(String sessionKey, String question, int topK, String tags) {
        String text = question == null ? "" : question.strip();
        if (text.isBlank()) {
            return List.of();
        }
        int limit = topK <= 0 ? properties.topK() : topK;
        List<String> plannedQueries = queryPlanner == null
                ? List.of(text)
                : queryPlanner.planQueries(text);
        if (plannedQueries.isEmpty()) {
            plannedQueries = List.of(text);
        }
        Map<String, KnowledgeSearchResult> deduplicated = new LinkedHashMap<>();
        List<String> tagList = parseTags(tags);
        String safeSession = sessionKey == null ? "" : sessionKey.strip();
        for (String query : plannedQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            List<KnowledgeSearchResult> results = vectorStore.search(
                    safeSession,
                    embeddingService.embed(query),
                    limit,
                    tagList);
            for (KnowledgeSearchResult result : results) {
                if (result == null || result.score() < properties.minScore()) {
                    continue;
                }
                String key = result.documentId() + ":" + result.chunkIndex();
                KnowledgeSearchResult existing = deduplicated.get(key);
                if (existing == null || result.score() > existing.score()) {
                    deduplicated.put(key, result);
                }
            }
        }
        return new ArrayList<>(deduplicated.values()).stream()
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
