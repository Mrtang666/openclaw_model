package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.bailian.BailianProperties;
import com.example.spring.weather.WeatherProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "ilink.bot.enabled=false",
        "memory.data-directory=target/test-memory",
        "spring.config.import=classpath:external-test.evn[.properties]"
    })
class WeChatBotPropertiesTests {
    @Autowired
    private WeChatBotProperties botProperties;

    @Autowired
    private BailianProperties bailianProperties;

    @Autowired
    private WeatherProperties weatherProperties;

    @Test
    void bindsExternalConfigurationAndChineseText() {
        assertThat(botProperties.getModelErrorReply())
            .isEqualTo("抱歉，处理消息时出现错误，请稍后再试。");
        assertThat(botProperties.isTypingIndicatorEnabled()).isTrue();
        assertThat(botProperties.getTypingPreviewDelay()).isEqualTo(java.time.Duration.ofMillis(600));
        assertThat(bailianProperties.isConfigured()).isTrue();
        assertThat(bailianProperties.getApiKey()).isEqualTo("test-runtime-key");
        assertThat(bailianProperties.getVisionModel()).isEqualTo("qwen3-vl-plus");
        assertThat(weatherProperties.isConfigured()).isTrue();
    }
}
