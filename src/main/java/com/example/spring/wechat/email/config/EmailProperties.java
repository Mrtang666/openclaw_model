package com.example.spring.wechat.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        boolean enabled,
        String provider,
        Smtp smtp,
        String allowedRecipients,
        boolean requireConfirmationForNonWhitelist,
        int pendingDraftTtlMinutes,
        int maxBodyChars) {

    public EmailProperties {
        provider = safeOrDefault(provider, "qq");
        smtp = smtp == null ? Smtp.defaults() : smtp;
        allowedRecipients = safe(allowedRecipients);
        pendingDraftTtlMinutes = pendingDraftTtlMinutes <= 0 ? 10 : pendingDraftTtlMinutes;
        maxBodyChars = maxBodyChars <= 0 ? 8_000 : maxBodyChars;
    }

    public Set<String> allowedRecipientSet() {
        if (allowedRecipients.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedRecipients.split(","))
                .map(EmailProperties::normalizeEmail)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isAllowedRecipient(String address) {
        String normalized = normalizeEmail(address);
        return !normalized.isBlank() && allowedRecipientSet().contains(normalized);
    }

    public String fromAddress() {
        if (!smtp.from().isBlank()) {
            return smtp.from();
        }
        return smtp.username();
    }

    public static String normalizeEmail(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        String result = safe(value);
        return result.isBlank() ? fallback : result;
    }

    public record Smtp(
            String host,
            int port,
            boolean sslEnabled,
            String username,
            String password,
            String from,
            int timeoutMs) {

        public Smtp {
            host = safeOrDefault(host, "smtp.qq.com");
            port = port <= 0 ? 465 : port;
            username = safe(username);
            password = safe(password);
            from = safe(from);
            timeoutMs = timeoutMs <= 0 ? 15_000 : timeoutMs;
        }

        public static Smtp defaults() {
            return new Smtp("smtp.qq.com", 465, true, "", "", "", 15_000);
        }
    }
}
