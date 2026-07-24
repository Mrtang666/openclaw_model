package com.example.spring.config;

import java.net.URI;

/**
 * 配置脱敏工具。
 *
 * <p>用于日志输出配置状态时隐藏真实 API Key，只保留首尾少量字符方便排查是否填错。
 * Host 提取只展示域名，避免把 URL 中可能存在的路径参数或查询参数打进日志。</p>
 */
public final class SecretMasker {

    private SecretMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "MISSING";
        }
        String text = value.strip();
        if (text.length() <= 8) {
            return "SET";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }

    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return "MISSING";
        }
        try {
            URI uri = URI.create(url.strip());
            return uri.getHost() == null || uri.getHost().isBlank() ? "INVALID_URL" : uri.getHost();
        } catch (IllegalArgumentException exception) {
            return "INVALID_URL";
        }
    }

    public static String present(String value) {
        return value == null || value.isBlank() ? "MISSING" : "SET";
    }
}
