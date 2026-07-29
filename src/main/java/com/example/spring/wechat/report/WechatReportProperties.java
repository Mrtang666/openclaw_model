package com.example.spring.wechat.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "wechat.report")
public class WechatReportProperties {

    private boolean enabled = true;
    private Path storageDir = Path.of("data", "reports");
    private String publicBaseUrl = "";
    private Duration ttl = Duration.ofHours(48);
    private int textLengthThreshold = 900;
    private int veryLongTextThreshold = 2000;
    private int itemCountThreshold = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(Path storageDir) {
        this.storageDir = storageDir == null ? Path.of("data", "reports") : storageDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.strip();
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofHours(48) : ttl;
    }

    public int getTextLengthThreshold() {
        return textLengthThreshold;
    }

    public void setTextLengthThreshold(int textLengthThreshold) {
        this.textLengthThreshold = textLengthThreshold > 0 ? textLengthThreshold : 900;
    }

    public int getVeryLongTextThreshold() {
        return veryLongTextThreshold;
    }

    public void setVeryLongTextThreshold(int veryLongTextThreshold) {
        this.veryLongTextThreshold = veryLongTextThreshold > 0 ? veryLongTextThreshold : 2000;
    }

    public int getItemCountThreshold() {
        return itemCountThreshold;
    }

    public void setItemCountThreshold(int itemCountThreshold) {
        this.itemCountThreshold = itemCountThreshold > 0 ? itemCountThreshold : 5;
    }
}
