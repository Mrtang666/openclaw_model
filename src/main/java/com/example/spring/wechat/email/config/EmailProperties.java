package com.example.spring.wechat.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private boolean enabled = true;
    private String host = "smtp.qq.com";
    private int port = 465;
    private boolean ssl = true;
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
    private List<String> allowedPaths = List.of(
            "data/downloads",
            "data/email/attachments",
            "generated-files",
            "generated-images",
            "data/wechat/documents",
            "data/wechat/images",
            "data/wechat/videos");
    private Duration pendingTtl = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = safe(host, "smtp.qq.com");
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port > 0 ? port : 465;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = safe(username, "");
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = safe(password, "");
    }

    public String getFrom() {
        return from == null || from.isBlank() ? username : from;
    }

    public void setFrom(String from) {
        this.from = safe(from, "");
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

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
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
}
