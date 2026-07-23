package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.exception.KnowledgeBaseException;
import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.model.KnowledgeMetadata;
import com.example.spring.wechat.knowledge.model.KnowledgeTextChunk;
import com.example.spring.wechat.knowledge.repository.KnowledgeRepository;
import com.example.spring.wechat.knowledge.vector.KnowledgeVector;
import com.example.spring.wechat.knowledge.vector.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 知识入库编排服务：清洗、去重、切分、向量化、写入 MySQL 和 Qdrant。
 */
@Service
public class KnowledgeIngestionService {

    private final KnowledgeRepository repository;
    private final KnowledgeChunkService chunkService;
    private final KnowledgeEmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeMetadataEnhancer metadataEnhancer;

    @Autowired
    public KnowledgeIngestionService(
            KnowledgeRepository repository,
            KnowledgeChunkService chunkService,
            KnowledgeEmbeddingService embeddingService,
            VectorStore vectorStore,
            KnowledgeMetadataEnhancer metadataEnhancer) {
        this.repository = repository;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.metadataEnhancer = metadataEnhancer;
    }

    public KnowledgeIngestionService(
            KnowledgeRepository repository,
            KnowledgeChunkService chunkService,
            KnowledgeEmbeddingService embeddingService,
            VectorStore vectorStore) {
        this(repository, chunkService, embeddingService, vectorStore, null);
    }

    public KnowledgeIngestionResult add(
            String sessionKey,
            String title,
            String content,
            String sourceType,
            String sourceUrl,
            String tags) {
        String safeContent = content == null ? "" : content.strip();
        if (safeContent.isBlank()) {
            throw new KnowledgeBaseException("知识内容不能为空");
        }
        String contentHash = sha256(safe(sessionKey) + "\n" + safeContent);
        var existing = repository.findActiveByHash(safe(sessionKey), contentHash);
        if (existing.isPresent()) {
            KnowledgeDocument document = existing.get();
            repository.log(safe(sessionKey), "ADD_DUPLICATE", document.id(), title, "知识已存在", Instant.now());
            return new KnowledgeIngestionResult(document.id(), document.title(), document.chunkCount(), true);
        }

        List<KnowledgeTextChunk> chunks = chunkService.split(safeContent);
        if (chunks.isEmpty()) {
            throw new KnowledgeBaseException("知识内容切分后为空");
        }
        KnowledgeMetadata metadata = metadataEnhancer == null
                ? new KnowledgeMetadata("", "", List.of())
                : metadataEnhancer.enhance(title, safeContent, sourceType, sourceUrl);
        String finalTitle = metadata.title().isBlank() ? safeTitle(title) : metadata.title();
        String finalTags = metadata.tags().isEmpty() ? tags : String.join(",", metadata.tags());

        KnowledgeDocument document = repository.createDocument(
                safe(sessionKey),
                finalTitle,
                safeDefault(sourceType, "text"),
                safe(sourceUrl),
                safe(finalTags),
                contentHash,
                chunks.size(),
                Instant.now());
        List<KnowledgeVector> vectors = new ArrayList<>();
        List<String> tagList = parseTags(tags);
        for (KnowledgeTextChunk chunk : chunks) {
            vectors.add(new KnowledgeVector(
                    UUID.nameUUIDFromBytes((document.id() + ":" + chunk.index()).getBytes(StandardCharsets.UTF_8)).toString(),
                    embeddingService.embed(chunk.content()),
                    safe(sessionKey),
                    document.id(),
                    document.title(),
                    chunk.index(),
                    chunk.content(),
                    document.sourceType(),
                    document.sourceUrl(),
                    tagList));
        }
        vectorStore.upsert(vectors);
        repository.log(safe(sessionKey), "ADD", document.id(), title, "知识入库成功，chunks=" + chunks.size(), Instant.now());
        return new KnowledgeIngestionResult(document.id(), document.title(), chunks.size(), false);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new KnowledgeBaseException("知识内容哈希计算失败", exception);
        }
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，]"))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String safeTitle(String title) {
        String value = safe(title);
        return value.isBlank() ? "未命名知识" : value;
    }

    private String safeDefault(String value, String fallback) {
        String text = safe(value);
        return text.isBlank() ? fallback : text;
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
