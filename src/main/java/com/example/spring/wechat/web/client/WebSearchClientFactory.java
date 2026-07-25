package com.example.spring.wechat.web.client;

import com.example.spring.wechat.web.config.WebToolProperties;
import com.example.spring.wechat.web.mcp.McpToolClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 根据配置选择网页搜索客户端。
 */
@Configuration
public class WebSearchClientFactory {

    @Bean
    public WebSearchClient webSearchClient(
            RestClient.Builder builder,
            McpToolClient mcpToolClient,
            WebToolProperties properties,
            @Value("${dashscope.base-url:}") String dashscopeBaseUrl,
            @Value("${openclaw.dashscope.model:${dashscope.model:qwen3.7-max-2026-06-08}}") String chatModel) {
        if ("bailian-mcp".equalsIgnoreCase(properties.search().provider())
                || "bailian".equalsIgnoreCase(properties.search().provider())) {
            return new BailianWebSearchClient(
                    builder,
                    mcpToolClient,
                    properties.search().endpoint(),
                    properties.search().apiKey(),
                    dashscopeBaseUrl,
                    chatModel);
        }
        return new NoopWebSearchClient();
    }
}
