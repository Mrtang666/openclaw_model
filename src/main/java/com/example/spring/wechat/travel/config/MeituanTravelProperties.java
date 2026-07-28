package com.example.spring.wechat.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meituan.travel")
public record MeituanTravelProperties(
        boolean enabled,
        String token,
        String executable,
        String cliScript,
        String channel,
        int timeoutMs,
        int maxOutputBytes) {

    public MeituanTravelProperties {
        token = token == null ? "" : token.strip();
        executable = executable == null || executable.isBlank() ? "ht-ai" : executable.strip();
        cliScript = cliScript == null ? "" : cliScript.strip();
        channel = channel == null || channel.isBlank() ? "meituan-developer" : channel.strip();
        timeoutMs = timeoutMs <= 0 ? 125_000 : timeoutMs;
        maxOutputBytes = maxOutputBytes <= 0 ? 2_097_152 : maxOutputBytes;
    }
}
