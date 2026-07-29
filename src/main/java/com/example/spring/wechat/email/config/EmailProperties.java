package com.example.spring.wechat.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private boolean enabled = true;

    private String provider = "qq";
    private Smtp smtp = Smtp.defaults();
    private String allowedRecipients = "";
    private boolean requireConfirmationForNonWhitelist = true;
    private int pendingDraftTtlMinutes = 10;
    private int maxBodyChars = 8_000;

    private String host = "";
    private Integer port;
    private Boolean ssl;
    private String username = "";
    private String password = "";
    private String from = "";

    private long maxAttachmentSizeMb = 25;
    private Path workDir = Path.of("data", "email", "attachments");
    private boolean receiveEnabled = true;

    private String imapHost = "imap.qq.com";
    private int imapPort = 993;
    private boolean imapSsl = true;

    private int queryLimit = 10;
    private int queryScanLimit = 100;
    private Path attachmentDownloadDir = Path.of("data", "downloads", "email", "attachments");
    private List<String> allowedPaths = defaultAllowedPaths();
    private Duration pendingTtl = Duration.ofMinutes(10);

    public EmailProperties() {
    }

    public EmailProperties(
            boolean enabled,
            String provider,
            Smtp smtp,
            String allowedRecipients,
            boolean requireConfirmationForNonWhitelist,
            int pendingDraftTtlMinutes,
            int maxBodyChars) {
        this.enabled = enabled;
        setProvider(provider);
        setSmtp(smtp);
        setAllowedRecipients(allowedRecipients);
        this.requireConfirmationForNonWhitelist = requireConfirmationForNonWhitelist;
        setPendingDraftTtlMinutes(pendingDraftTtlMinutes);
        setMaxBodyChars(maxBodyChars);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String provider() {
        return provider;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = safe(provider, "qq");
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp == null ? Smtp.defaults() : smtp;
    }

    public Smtp smtp() {
        Smtp value = smtp == null ? Smtp.defaults() : smtp;
        return new Smtp(
                getHost(),
                getPort(),
                isSsl(),
                getUsername(),
                getPassword(),
                getFrom(),
                value.timeoutMs());
    }

    public String getAllowedRecipients() {
        return allowedRecipients;
    }

    public void setAllowedRecipients(String allowedRecipients) {
        this.allowedRecipients = safeRaw(allowedRecipients);
    }

    public String allowedRecipients() {
        return allowedRecipients;
    }

    public boolean isRequireConfirmationForNonWhitelist() {
        return requireConfirmationForNonWhitelist;
    }

    public boolean requireConfirmationForNonWhitelist() {
        return requireConfirmationForNonWhitelist;
    }

    public void setRequireConfirmationForNonWhitelist(boolean requireConfirmationForNonWhitelist) {
        this.requireConfirmationForNonWhitelist = requireConfirmationForNonWhitelist;
    }

    public int getPendingDraftTtlMinutes() {
        return pendingDraftTtlMinutes;
    }

    public int pendingDraftTtlMinutes() {
        return pendingDraftTtlMinutes;
    }

    public void setPendingDraftTtlMinutes(int pendingDraftTtlMinutes) {
        this.pendingDraftTtlMinutes = pendingDraftTtlMinutes > 0 ? pendingDraftTtlMinutes : 10;
    }

    public int getMaxBodyChars() {
        return maxBodyChars;
    }

    public int maxBodyChars() {
        return maxBodyChars;
    }

    public void setMaxBodyChars(int maxBodyChars) {
        this.maxBodyChars = maxBodyChars > 0 ? maxBodyChars : 8_000;
    }

    public String getHost() {
        return firstNonBlank(host, smtp == null ? "" : smtp.host(), "smtp.qq.com");
    }

    public void setHost(String host) {
        this.host = safeRaw(host);
    }

    public int getPort() {
        if (port != null && port > 0) {
            return port;
        }
        return smtp == null ? 465 : smtp.port();
    }

    public void setPort(int port) {
        this.port = port > 0 ? port : 465;
    }

    public boolean isSsl() {
        if (ssl != null) {
            return ssl;
        }
        return smtp == null || smtp.sslEnabled();
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getUsername() {
        return firstNonBlank(username, smtp == null ? "" : smtp.username(), "");
    }

    public void setUsername(String username) {
        this.username = safeRaw(username);
    }

    public String getPassword() {
        return firstNonBlank(password, smtp == null ? "" : smtp.password(), "");
    }

    public void setPassword(String password) {
        this.password = safeRaw(password);
    }

    public String getFrom() {
        return firstNonBlank(from, smtp == null ? "" : smtp.from(), getUsername());
    }

    public void setFrom(String from) {
        this.from = safeRaw(from);
    }

    public String fromAddress() {
        return getFrom();
    }

    public long getMaxAttachmentSizeMb() {
        return maxAttachmentSizeMb;
    }

    public void setMaxAttachmentSizeMb(long maxAttachmentSizeMb) {
        this.maxAttachmentSizeMb = maxAttachmentSizeMb > 0 ? maxAttachmentSizeMb : 25;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir == null ? Path.of("data", "email", "attachments") : workDir;
    }

    public boolean isReceiveEnabled() {
        return receiveEnabled;
    }

    public void setReceiveEnabled(boolean receiveEnabled) {
        this.receiveEnabled = receiveEnabled;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = safe(imapHost, "imap.qq.com");
    }

    public int getImapPort() {
        return imapPort;
    }

    public void setImapPort(int imapPort) {
        this.imapPort = imapPort > 0 ? imapPort : 993;
    }

    public boolean isImapSsl() {
        return imapSsl;
    }

    public void setImapSsl(boolean imapSsl) {
        this.imapSsl = imapSsl;
    }

    public int getQueryLimit() {
        return queryLimit;
    }

    public void setQueryLimit(int queryLimit) {
        this.queryLimit = queryLimit > 0 ? queryLimit : 10;
    }

    public int getQueryScanLimit() {
        return queryScanLimit;
    }

    public void setQueryScanLimit(int queryScanLimit) {
        this.queryScanLimit = queryScanLimit > 0 ? queryScanLimit : 100;
    }

    public Path getAttachmentDownloadDir() {
        return attachmentDownloadDir;
    }

    public void setAttachmentDownloadDir(Path attachmentDownloadDir) {
        this.attachmentDownloadDir = attachmentDownloadDir == null
                ? Path.of("data", "downloads", "email", "attachments")
                : attachmentDownloadDir;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths == null || allowedPaths.isEmpty()
                ? defaultAllowedPaths()
                : allowedPaths.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }

    public Duration getPendingTtl() {
        return pendingTtl;
    }

    public void setPendingTtl(Duration pendingTtl) {
        this.pendingTtl = pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()
                ? Duration.ofMinutes(10)
                : pendingTtl;
    }

    public long maxAttachmentBytes() {
        return maxAttachmentSizeMb * 1024L * 1024L;
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

    public static String normalizeEmail(String value) {
        return safeRaw(value).toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String firstValue = safeRaw(first);
        if (!firstValue.isBlank()) {
            return firstValue;
        }
        String secondValue = safeRaw(second);
        return secondValue.isBlank() ? fallback : secondValue;
    }

    private static String safe(String value, String fallback) {
        String result = safeRaw(value);
        return result.isBlank() ? fallback : result;
    }

    private static String safeRaw(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> defaultAllowedPaths() {
        return List.of(
                "data/downloads",
                "data/email/attachments",
                "generated-files",
                "generated-images",
                "data/wechat/documents",
                "data/wechat/images",
                "data/wechat/videos");
    }

    public static class Smtp {
        private String host = "smtp.qq.com";
        private int port = 465;
        private boolean sslEnabled = true;
        private String username = "";
        private String password = "";
        private String from = "";
        private int timeoutMs = 15_000;

        public Smtp() {
        }

        public Smtp(
                String host,
                int port,
                boolean sslEnabled,
                String username,
                String password,
                String from,
                int timeoutMs) {
            setHost(host);
            setPort(port);
            setSslEnabled(sslEnabled);
            setUsername(username);
            setPassword(password);
            setFrom(from);
            setTimeoutMs(timeoutMs);
        }

        public static Smtp defaults() {
            return new Smtp("smtp.qq.com", 465, true, "", "", "", 15_000);
        }

        public String getHost() {
            return host;
        }

        public String host() {
            return host;
        }

        public void setHost(String host) {
            this.host = safe(host, "smtp.qq.com");
        }

        public int getPort() {
            return port;
        }

        public int port() {
            return port;
        }

        public void setPort(int port) {
            this.port = port > 0 ? port : 465;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public boolean sslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }

        public String getUsername() {
            return username;
        }

        public String username() {
            return username;
        }

        public void setUsername(String username) {
            this.username = safeRaw(username);
        }

        public String getPassword() {
            return password;
        }

        public String password() {
            return password;
        }

        public void setPassword(String password) {
            this.password = safeRaw(password);
        }

        public String getFrom() {
            return from;
        }

        public String from() {
            return from;
        }

        public void setFrom(String from) {
            this.from = safeRaw(from);
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public int timeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs > 0 ? timeoutMs : 15_000;
        }
    }
}
