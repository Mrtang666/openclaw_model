package com.example.spring.wechat.web.service;

import com.example.spring.wechat.web.client.WebSearchClient;
import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.web.model.WebSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网页搜索服务。
 */
@Service
public class WebSearchService {

    private final WebSearchClient client;
    private final WebToolProperties properties;

    public WebSearchService(WebSearchClient client, WebToolProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public List<WebSearchResult> search(String query, int limit, String freshness, String language) {
        return client.search(
                query,
                limit <= 0 ? properties.search().limit() : limit,
                freshness == null || freshness.isBlank() ? "any" : freshness,
                language == null || language.isBlank() ? "zh-CN" : language);
    }
}
