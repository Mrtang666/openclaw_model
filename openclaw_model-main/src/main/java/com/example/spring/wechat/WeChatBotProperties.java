package com.example.spring.wechat;

import java.time.Duration;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ilink.bot")
public class WeChatBotProperties {
    private boolean enabled = true;
    private Duration retryDelay = Duration.ofSeconds(2);
    private boolean typingIndicatorEnabled = true;
    private Duration typingPreviewDelay = Duration.ofMillis(600);
    private String modelErrorReply = "抱歉，处理消息时出现错误，请稍后再试。";
    private Path lockFile = Path.of(
        System.getProperty("user.home"), ".openclaw-model", "wechat-ilink.lock");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public boolean isTypingIndicatorEnabled() {
        return typingIndicatorEnabled;
    }

    public void setTypingIndicatorEnabled(boolean typingIndicatorEnabled) {
        this.typingIndicatorEnabled = typingIndicatorEnabled;
    }

    public Duration getTypingPreviewDelay() {
        return typingPreviewDelay;
    }

    public void setTypingPreviewDelay(Duration typingPreviewDelay) {
        this.typingPreviewDelay = typingPreviewDelay;
    }

    public String getModelErrorReply() {
        return modelErrorReply;
    }

    public void setModelErrorReply(String modelErrorReply) {
        this.modelErrorReply = modelErrorReply;
    }

    public Path getLockFile() {
        return lockFile;
    }

    public void setLockFile(Path lockFile) {
        this.lockFile = lockFile;
    }
}
