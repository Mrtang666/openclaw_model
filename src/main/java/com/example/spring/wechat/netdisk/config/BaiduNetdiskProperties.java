package com.example.spring.wechat.netdisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百度网盘相关配置。
 *
 * <p>这里同时保留 AppID、AppKey、SecretKey、SignKey 这类平台凭证，便于后续对接百度侧不同能力。
 */
@ConfigurationProperties(prefix = "baidu.netdisk")
public record BaiduNetdiskProperties(
        boolean enabled,
        String appId,
        String appKey,
        String oauthClientId,
        String secretKey,
        String signKey,
        String redirectUri,
        String authBaseUrl,
        String tokenUrl,
        String mcpSseBaseUrl,
        String tokenEncryptionKey,
        int authStateTtlMinutes,
        int pendingActionTtlMinutes,
        int mcpTimeoutMs,
        int contextLimit,
        String defaultUploadPath) {

    public BaiduNetdiskProperties {
        appId = safe(appId);
        appKey = safe(appKey);
        oauthClientId = safe(oauthClientId);
        secretKey = safe(secretKey);
        signKey = safe(signKey);
        redirectUri = safe(redirectUri);
        authBaseUrl = safe(authBaseUrl);
        tokenUrl = safe(tokenUrl);
        mcpSseBaseUrl = safeOrDefault(mcpSseBaseUrl, "https://mcp-pan.baidu.com/sse");
        tokenEncryptionKey = safe(tokenEncryptionKey);
        authStateTtlMinutes = authStateTtlMinutes <= 0 ? 10 : authStateTtlMinutes;
        pendingActionTtlMinutes = pendingActionTtlMinutes <= 0 ? 30 : pendingActionTtlMinutes;
        mcpTimeoutMs = mcpTimeoutMs <= 0 ? 20_000 : mcpTimeoutMs;
        contextLimit = contextLimit <= 0 ? 5 : contextLimit;
        defaultUploadPath = safeOrDefault(defaultUploadPath, "/OpenClaw/");
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
