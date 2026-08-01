package com.example.spring.wechat.browser.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BrowserAutomationProperties.class)
public class BrowserAutomationConfiguration {
}
