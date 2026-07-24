package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.repository.KnowledgeRepository;
import com.example.spring.wechat.knowledge.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库管理服务。
 */
@Service
public class KnowledgeManageService {

    private final KnowledgeRepository repository;
    private final VectorStore vectorStore;

    public KnowledgeManageService(KnowledgeRepository repository, VectorStore vectorStore) {
        this.repository = repository;
        this.vectorStore = vectorStore;
    }

    public List<KnowledgeDocument> list(String sessionKey, String keyword, int limit) {
        return list(sessionKey, keyword, "", "", limit);
    }

    public List<KnowledgeDocument> list(String sessionKey, String keyword, String tags, String sourceType, int limit) {
        String tagFilter = safe(tags).toLowerCase(java.util.Locale.ROOT);
        String sourceFilter = safe(sourceType).toLowerCase(java.util.Locale.ROOT);
        return repository.listDocuments(sessionKey, keyword, Math.max(1, limit <= 0 ? 10 : limit))
                .stream()
                .filter(document -> tagFilter.isBlank() || safe(document.tags()).toLowerCase(java.util.Locale.ROOT).contains(tagFilter))
                .filter(document -> sourceFilter.isBlank() || safe(document.sourceType()).toLowerCase(java.util.Locale.ROOT).equals(sourceFilter))
                .toList();
    }

    public Optional<KnowledgeDocument> detail(String sessionKey, long documentId) {
        return repository.findDocument(sessionKey, documentId);
    }

    public boolean delete(String sessionKey, long documentId) {
        boolean deleted = repository.softDelete(sessionKey, documentId, Instant.now());
        if (deleted) {
            vectorStore.deleteDocument(sessionKey, documentId);
            repository.log(sessionKey, "DELETE", documentId, "", "知识删除成功", Instant.now());
        }
        return deleted;
    }

    public boolean updateTitle(String sessionKey, long documentId, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        boolean updated = repository.updateTitle(sessionKey, documentId, title.strip(), Instant.now());
        if (updated) {
            repository.log(sessionKey, "UPDATE_TITLE", documentId, title, "知识标题更新成功", Instant.now());
        }
        return updated;
    }

    public boolean updateTags(String sessionKey, long documentId, String tags) {
        if (tags == null || tags.isBlank()) {
            return false;
        }
        boolean updated = repository.updateTags(sessionKey, documentId, tags.strip(), Instant.now());
        if (updated) {
            repository.log(sessionKey, "UPDATE_TAGS", documentId, tags, "知识标签更新成功", Instant.now());
        }
        return updated;
    }

    public int batchDelete(String sessionKey, String keyword, String tags, String sourceType) {
        List<KnowledgeDocument> documents = list(sessionKey, keyword, tags, sourceType, 100);
        int count = 0;
        for (KnowledgeDocument document : documents) {
            if (delete(sessionKey, document.id())) {
                count++;
            }
        }
        return count;
    }

    public boolean reindex(String sessionKey, long documentId) {
        repository.log(sessionKey, "REINDEX_UNSUPPORTED", documentId, "", "当前版本缺少原始分块正文，无法安全重新向量化", Instant.now());
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
