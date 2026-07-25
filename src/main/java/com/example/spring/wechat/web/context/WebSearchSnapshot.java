package com.example.spring.wechat.web.context;

import com.example.spring.wechat.web.model.WebSearchResult;

import java.time.Instant;
import java.util.List;

/**
 * 一次网页搜索的轻量快照，只保存标题、链接、摘要等引用信息。
 */
public record WebSearchSnapshot(
        String query,
        List<WebSearchResult> results,
        Instant createdAt) {

    public WebSearchSnapshot {
        query = query == null ? "" : query.strip();
        results = results == null ? List.of() : List.copyOf(results);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
