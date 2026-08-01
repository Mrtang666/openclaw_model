package com.example.spring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingPropertiesTests {

    @Test
    void consoleLoggingUsesConcisePatternAndCalmDefaults() throws IOException {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("logging.pattern.console"))
                .isEqualTo("${LOGGING_PATTERN_CONSOLE:%d{HH:mm:ss} %-5level %-36.36logger{36} - %msg%n}");
        assertThat(properties.getProperty("logging.level.com.example.spring.wechat.bot.WechatBotService"))
                .isEqualTo("${LOGGING_LEVEL_WECHAT_BOT:INFO}");
        assertThat(properties.getProperty("logging.level.com.example.spring.wechat.conversation.WechatConversationService"))
                .isEqualTo("${LOGGING_LEVEL_WECHAT_CONVERSATION:INFO}");
    }

    private Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }
        return properties;
    }
}
