package com.example.spring.wechat.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant 向量库连接配置。
 */
@ConfigurationProperties(prefix = "qdrant")
public record QdrantProperties(
        String host,
        int httpPort,
        String apiKey,
        String collection,
        String distance,
        int vectorSize) {

    public QdrantProperties {
        host = host == null || host.isBlank() ? "localhost" : host.strip();
        httpPort = httpPort <= 0 ? 6333 : httpPort;
        apiKey = apiKey == null ? "" : apiKey.strip();
        collection = collection == null || collection.isBlank() ? "openclaw_knowledge" : collection.strip();
        distance = distance == null || distance.isBlank() ? "Cosine" : distance.strip();
        vectorSize = Math.max(0, vectorSize);
    }

    public String baseUrl() {
        return "http://" + host + ":" + httpPort;
    }
}
