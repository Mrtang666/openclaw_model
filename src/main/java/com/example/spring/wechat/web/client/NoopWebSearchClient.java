package com.example.spring.wechat.web.client;

import com.example.spring.wechat.web.exception.WebToolException;
import com.example.spring.wechat.web.model.WebSearchResult;

import java.util.List;

/**
 * 未配置搜索服务时的默认实现。
 */
public class NoopWebSearchClient implements WebSearchClient {

    @Override
    public List<WebSearchResult> search(String query, int limit, String freshness, String language) {
        throw new WebToolException("暂未配置网页搜索服务，请配置 WEB_SEARCH_PROVIDER、WEB_SEARCH_ENDPOINT 和 WEB_SEARCH_API_KEY");
    }
}
