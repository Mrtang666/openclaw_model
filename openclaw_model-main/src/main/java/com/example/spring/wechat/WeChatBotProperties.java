package com.example.spring.wechat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ilink.bot")
public class WeChatBotProperties {
    private boolean enabled = true;
    private String fixedReply = "你好，我已收到你的消息。";
    private Duration retryDelay = Duration.ofSeconds(2);
    private String qrCodeOutput = "target/ilink-login-qr.png";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFixedReply() {
        return fixedReply;
    }

    public void setFixedReply(String fixedReply) {
        this.fixedReply = fixedReply;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public String getQrCodeOutput() {
        return qrCodeOutput;
    }

    public void setQrCodeOutput(String qrCodeOutput) {
        this.qrCodeOutput = qrCodeOutput;
    }
}
