package com.example.spring.wechat.taxi.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "didi.mcp")
public record DidiMcpProperties(
        String endpoint,
        String key,
        int timeoutMs,
        boolean enabled) {

    public DidiMcpProperties {
        endpoint = endpoint == null || endpoint.isBlank()
                ? "https://mcp.didichuxing.com/mcp-servers"
                : endpoint.strip();
        key = key == null ? "" : key.strip();
        timeoutMs = timeoutMs <= 0 ? 15_000 : timeoutMs;
    }
}
