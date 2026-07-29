package com.example.spring.xhs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xhs.console")
public record XhsConsoleProperties(
        boolean enabled,
        String baseUrl,
        boolean autoOpen) {

    public XhsConsoleProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
    }
}
