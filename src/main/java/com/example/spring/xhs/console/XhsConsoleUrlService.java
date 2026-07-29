package com.example.spring.xhs.console;

import com.example.spring.xhs.config.XhsConsoleProperties;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class XhsConsoleUrlService {

    private final XhsConsoleProperties properties;
    private final ApplicationContext applicationContext;

    public XhsConsoleUrlService(XhsConsoleProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    public String pageUrl() {
        if (!properties.enabled()) {
            throw new IllegalStateException("小红书舆情管理台未启用");
        }
        return baseUrl() + "/xhs-console/index.html";
    }

    public String postOpenUrl(long postId) {
        return baseUrl() + "/api/xhs-console/posts/" + postId + "/open";
    }

    private String baseUrl() {
        String baseUrl = properties.baseUrl();
        if (baseUrl.isBlank()) {
            if (!(applicationContext instanceof WebServerApplicationContext webServerContext)) {
                throw new IllegalStateException("小红书舆情管理台需要 Web Server 上下文");
            }
            baseUrl = "http://127.0.0.1:" + webServerContext.getWebServer().getPort();
        }
        return baseUrl;
    }
}
