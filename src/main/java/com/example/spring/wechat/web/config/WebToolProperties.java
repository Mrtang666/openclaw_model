package com.example.spring.wechat.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网页阅读与搜索配置。
 */
@ConfigurationProperties(prefix = "web")
public record WebToolProperties(
        Read read,
        Search search,
        Cache cache) {

    public WebToolProperties {
        read = read == null ? new Read(10_000, 2_097_152) : read;
        search = search == null ? new Search("none", "", "", 5) : search;
        cache = cache == null ? new Cache(24) : cache;
    }

    public record Read(int timeoutMs, int maxBytes) {

        public Read {
            timeoutMs = timeoutMs <= 0 ? 10_000 : timeoutMs;
            maxBytes = maxBytes <= 0 ? 2_097_152 : maxBytes;
        }
    }

    public record Search(String provider, String endpoint, String apiKey, int limit) {

        public Search {
            provider = provider == null || provider.isBlank() ? "none" : provider.strip();
            endpoint = endpoint == null ? "" : endpoint.strip();
            apiKey = apiKey == null ? "" : apiKey.strip();
            limit = limit <= 0 ? 5 : limit;
        }
    }

    public record Cache(int ttlHours) {

        public Cache {
            ttlHours = ttlHours <= 0 ? 24 : ttlHours;
        }
    }
}
