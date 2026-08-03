package com.example.spring.wechat.context;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryGraphRetriever {

    private final MemoryGraphRepository repository;

    public MemoryGraphRetriever(MemoryGraphRepository repository) {
        this.repository = repository;
    }

    public List<MemoryGraphNode> activeExtracts(String sessionKey, String topicKey, int limit) {
        if (repository == null || isBlank(topicKey)) {
            return List.of();
        }
        return repository.findRecentNodesByTopic(sessionKey, MemoryNodeType.ACTIVE_EXTRACT, topicKey, limit);
    }

    public List<MemoryGraphNode> recentTopics(String sessionKey, int limit) {
        if (repository == null) {
            return List.of();
        }
        return repository.findRecentNodes(sessionKey, MemoryNodeType.CONVERSATION_TOPIC, limit);
    }

    public List<MemoryGraphNode> recentSummaries(String sessionKey, int limit) {
        if (repository == null) {
            return List.of();
        }
        return repository.findRecentNodes(sessionKey, MemoryNodeType.CONVERSATION_SUMMARY, limit);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
