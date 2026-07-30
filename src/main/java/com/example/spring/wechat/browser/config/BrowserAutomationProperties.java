package com.example.spring.wechat.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.LinkedHashSet;
import java.util.List;

@ConfigurationProperties(prefix = "browser.automation")
public record BrowserAutomationProperties(
        boolean enabled,
        String mcpEndpoint,
        String apiKey,
        int timeoutMs,
        boolean allowExternalUrl,
        List<String> allowedHosts,
        String screenshotDir,
        boolean requireConfirmationForRiskyActions) {

    public BrowserAutomationProperties(
            boolean enabled,
            String mcpEndpoint,
            String apiKey,
            int timeoutMs,
            boolean allowExternalUrl,
            String allowedHosts,
            String screenshotDir,
            boolean requireConfirmationForRiskyActions) {
        this(
                enabled,
                mcpEndpoint,
                apiKey,
                timeoutMs,
                allowExternalUrl,
                parseHosts(allowedHosts),
                screenshotDir,
                requireConfirmationForRiskyActions);
    }

    @ConstructorBinding
    public BrowserAutomationProperties {
        mcpEndpoint = safeOrDefault(mcpEndpoint, "http://127.0.0.1:3333/mcp");
        apiKey = safe(apiKey);
        timeoutMs = timeoutMs <= 0 ? 30_000 : timeoutMs;
        allowedHosts = allowedHosts == null || allowedHosts.isEmpty()
                ? List.of("localhost", "127.0.0.1")
                : cleanHosts(allowedHosts);
        screenshotDir = safeOrDefault(screenshotDir, "data/browser/screenshots");
    }

    private static List<String> parseHosts(String hosts) {
        if (hosts == null || hosts.isBlank()) {
            return List.of();
        }
        return cleanHosts(List.of(hosts.split(",")));
    }

    private static List<String> cleanHosts(List<String> hosts) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String host : hosts) {
            if (host != null && !host.isBlank()) {
                cleaned.add(host.strip().toLowerCase());
            }
        }
        return cleaned.isEmpty() ? List.of("localhost", "127.0.0.1") : List.copyOf(cleaned);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }
}
