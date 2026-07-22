package com.example.spring.wechat.login;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WechatLoginPageUrlService {

    private final WechatLoginPageProperties properties;
    private final ApplicationContext applicationContext;

    public WechatLoginPageUrlService(
            WechatLoginPageProperties properties,
            ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    public String pageUrl(String sessionId) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.isBlank()) {
            if (!(applicationContext instanceof WebServerApplicationContext webServerContext)) {
                throw new IllegalStateException("微信登录页面需要 Web Server 上下文");
            }
            baseUrl = "http://127.0.0.1:" + webServerContext.getWebServer().getPort();
        }
        return stripTrailingSlash(baseUrl)
                + "/wechat-login/index.html?session="
                + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
