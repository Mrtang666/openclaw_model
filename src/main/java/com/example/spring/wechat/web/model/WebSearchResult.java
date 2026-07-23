package com.example.spring.wechat.web.model;

/**
 * 网页搜索结果。
 */
public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String source,
        String publishedAt) {
}
