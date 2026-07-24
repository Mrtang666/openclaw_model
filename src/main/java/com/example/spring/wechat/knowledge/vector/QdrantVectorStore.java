package com.example.spring.wechat.knowledge.vector;

import com.example.spring.wechat.knowledge.config.QdrantProperties;
import com.example.spring.wechat.knowledge.exception.KnowledgeBaseException;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant REST 实现，负责知识 chunk 的向量写入、搜索和删除。
 */
@Service
public class QdrantVectorStore implements VectorStore {

    private final RestClient restClient;
    private final QdrantProperties properties;
    private volatile Integer initializedVectorSize;

    public QdrantVectorStore(RestClient.Builder builder, QdrantProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public void upsert(List<KnowledgeVector> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int vectorSize = values.get(0).vector() == null ? 0 : values.get(0).vector().size();
        ensureCollection(vectorSize);
        List<Map<String, Object>> points = values.stream()
                .map(this::point)
                .toList();
        try {
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .body(Map.of("points", points))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new KnowledgeBaseException("Qdrant 知识向量写入失败", exception);
        }
    }

    @Override
    public List<KnowledgeSearchResult> search(String sessionKey, List<Float> queryVector, int topK, List<String> tags) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        ensureCollection(queryVector.size());
        try {
            JsonNode root = restClient.post()
                    .uri("/collections/{collection}/points/search", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .body(searchBody(sessionKey, queryVector, topK, tags))
                    .retrieve()
                    .body(JsonNode.class);
            return parseSearchResults(root);
        } catch (RestClientException exception) {
            throw new KnowledgeBaseException("Qdrant 知识检索失败", exception);
        }
    }

    @Override
    public void deleteDocument(String sessionKey, long documentId) {
        try {
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .body(Map.of("filter", filter(sessionKey, documentId, List.of())))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new KnowledgeBaseException("Qdrant 知识向量删除失败", exception);
        }
    }

    private synchronized void ensureCollection(int vectorSize) {
        int size = properties.vectorSize() > 0 ? properties.vectorSize() : vectorSize;
        if (size <= 0) {
            throw new KnowledgeBaseException("无法确定 Qdrant collection 向量维度");
        }
        if (initializedVectorSize != null && initializedVectorSize == size) {
            return;
        }
        try {
            restClient.get()
                    .uri("/collections/{collection}", properties.collection())
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .retrieve()
                    .toBodilessEntity();
            initializedVectorSize = size;
        } catch (RestClientException exception) {
            createCollection(size);
            initializedVectorSize = size;
        }
    }

    private void createCollection(int vectorSize) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", properties.distance());
        try {
            restClient.put()
                    .uri("/collections/{collection}", properties.collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .body(Map.of("vectors", vectors))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new KnowledgeBaseException("Qdrant collection 初始化失败", exception);
        }
    }

    private Map<String, Object> point(KnowledgeVector vector) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_key", vector.sessionKey());
        payload.put("document_id", vector.documentId());
        payload.put("title", vector.title());
        payload.put("chunk_index", vector.chunkIndex());
        payload.put("content", vector.content());
        payload.put("source_type", vector.sourceType());
        payload.put("source_url", vector.sourceUrl());
        payload.put("tags", vector.tags() == null ? List.of() : vector.tags());

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", vector.pointId());
        point.put("vector", vector.vector());
        point.put("payload", payload);
        return point;
    }

    private Map<String, Object> searchBody(String sessionKey, List<Float> queryVector, int topK, List<String> tags) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", Math.max(1, topK));
        body.put("with_payload", true);
        body.put("filter", filter(sessionKey, null, tags));
        return body;
    }

    private Map<String, Object> filter(String sessionKey, Long documentId, List<String> tags) {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(match("session_key", sessionKey == null ? "" : sessionKey.strip()));
        if (documentId != null) {
            must.add(match("document_id", documentId));
        }
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isBlank()) {
                    must.add(match("tags", tag.strip()));
                }
            }
        }
        return Map.of("must", must);
    }

    private Map<String, Object> match(String key, Object value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private List<KnowledgeSearchResult> parseSearchResults(JsonNode root) {
        JsonNode result = root == null ? null : root.path("result");
        if (result == null || !result.isArray()) {
            return List.of();
        }
        List<KnowledgeSearchResult> values = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode payload = item.path("payload");
            values.add(new KnowledgeSearchResult(
                    payload.path("document_id").asLong(),
                    payload.path("title").asText(""),
                    payload.path("chunk_index").asInt(),
                    payload.path("content").asText(""),
                    payload.path("source_type").asText(""),
                    payload.path("source_url").asText(""),
                    item.path("score").asDouble()));
        }
        return values;
    }

    private void addApiKey(org.springframework.http.HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("api-key", apiKey);
        }
    }
}
