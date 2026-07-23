package com.example.spring.wechat.web.client;

import com.example.spring.wechat.web.model.WebSearchResult;

import java.util.List;

/**
 * 网页搜索客户端抽象。
 */
public interface WebSearchClient {

    List<WebSearchResult> search(String query, int limit, String freshness, String language);
}
