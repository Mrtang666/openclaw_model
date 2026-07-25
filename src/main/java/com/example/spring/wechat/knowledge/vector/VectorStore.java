package com.example.spring.wechat.knowledge.vector;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;

import java.util.List;

/**
 * 向量库抽象，当前实现为 Qdrant，后续可替换为 Milvus、pgvector 等。
 */
public interface VectorStore {

    void upsert(List<KnowledgeVector> values);

    List<KnowledgeSearchResult> search(String sessionKey, List<Float> queryVector, int topK, List<String> tags);

    void deleteDocument(String sessionKey, long documentId);
}
