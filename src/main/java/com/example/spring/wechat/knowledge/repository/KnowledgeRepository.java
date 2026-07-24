package com.example.spring.wechat.knowledge.repository;

import com.example.spring.wechat.knowledge.model.KnowledgeDocument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库元数据仓储接口。
 */
public interface KnowledgeRepository {

    Optional<KnowledgeDocument> findActiveByHash(String sessionKey, String contentHash);

    KnowledgeDocument createDocument(
            String sessionKey,
            String title,
            String sourceType,
            String sourceUrl,
            String tags,
            String contentHash,
            int chunkCount,
            Instant now);

    List<KnowledgeDocument> listDocuments(String sessionKey, String keyword, int limit);

    Optional<KnowledgeDocument> findDocument(String sessionKey, long documentId);

    boolean softDelete(String sessionKey, long documentId, Instant now);

    default boolean updateTitle(String sessionKey, long documentId, String title, Instant now) {
        return false;
    }

    default boolean updateTags(String sessionKey, long documentId, String tags, Instant now) {
        return false;
    }

    void log(String sessionKey, String operation, Long documentId, String queryText, String resultSummary, Instant now);
}
