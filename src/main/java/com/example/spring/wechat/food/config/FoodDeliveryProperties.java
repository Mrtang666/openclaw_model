package com.example.spring.wechat.food.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "food.delivery")
public record FoodDeliveryProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        int timeoutMs,
        String elemeH5Url,
        String addressEncryptionKey) {

    public FoodDeliveryProperties {
        baseUrl = stripTrailingSlash(baseUrl);
        apiKey = safe(apiKey);
        timeoutMs = timeoutMs <= 0 ? 20_000 : timeoutMs;
        elemeH5Url = safeOrDefault(elemeH5Url, "https://h5.ele.me");
        addressEncryptionKey = safe(addressEncryptionKey);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String stripTrailingSlash(String value) {
        String result = safe(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
