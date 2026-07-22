package com.example.spring.wechat.login;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "wechat.login-page")
public class WechatLoginPageProperties {

    private String baseUrl = "";
    private Duration sessionTtl = Duration.ofMinutes(10);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()
                ? Duration.ofMinutes(10)
                : sessionTtl;
    }
}
