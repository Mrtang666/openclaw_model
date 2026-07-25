package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.exception.KnowledgeBaseException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用阿里百炼 OpenAI 兼容 embedding 接口，把文本转换为向量。
 */
@Service
public class DashScopeKnowledgeEmbeddingService implements KnowledgeEmbeddingService {

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DashScopeKnowledgeEmbeddingService(
            RestClient.Builder builder,
            @Value("${dashscope.embedding.api-key:}") String apiKey,
            @Value("${dashscope.embedding.base-url:}") String baseUrl,
            @Value("${dashscope.embedding.model:text-embedding-v4}") String model) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.restClient = builder.baseUrl(this.baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null || model.isBlank() ? "text-embedding-v4" : model.strip();
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new KnowledgeBaseException("向量化文本不能为空");
        }
        validateConfiguration();
        try {
            JsonNode root = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody(text))
                    .retrieve()
                    .body(JsonNode.class);
            return parseEmbedding(root);
        } catch (KnowledgeBaseException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new KnowledgeBaseException("百炼 embedding 接口暂时不可用", exception);
        }
    }

    private Map<String, Object> requestBody(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text.strip());
        return body;
    }

    private List<Float> parseEmbedding(JsonNode root) {
        JsonNode embedding = root == null ? null : root.path("data").path(0).path("embedding");
        if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
            throw new KnowledgeBaseException("百炼 embedding 接口未返回有效向量");
        }
        List<Float> values = new ArrayList<>();
        for (JsonNode node : embedding) {
            values.add((float) node.asDouble());
        }
        return values;
    }

    private void validateConfiguration() {
        if (apiKey.isBlank()) {
            throw new KnowledgeBaseException("未配置 DASHSCOPE_EMBEDDING_API_KEY 或 DASHSCOPE_API_KEY");
        }
        if (baseUrl.isBlank()) {
            throw new KnowledgeBaseException("未配置 DASHSCOPE_EMBEDDING_BASE_URL 或 DASHSCOPE_BASE_URL");
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
