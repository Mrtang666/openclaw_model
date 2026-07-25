package com.example.spring.wechat.knowledge.service;

import java.util.List;

/**
 * 文本向量化服务。
 */
public interface KnowledgeEmbeddingService {

    List<Float> embed(String text);
}
