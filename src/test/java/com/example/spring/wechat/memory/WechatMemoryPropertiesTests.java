package com.example.spring.wechat.memory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.fail;

class WechatMemoryPropertiesTests {

    @Test
    void providesSafeDefaultsForWechatMemoryPolicy() throws Exception {
        Class<?> propertiesType;
        try {
            propertiesType = Class.forName(
                    "com.example.spring.wechat.memory.config.WechatMemoryProperties");
        } catch (ClassNotFoundException exception) {
            fail("微信记忆配置类尚未实现");
            return;
        }
        Constructor<?> constructor = propertiesType.getDeclaredConstructors()[0];

        assertThatCode(() -> constructor.newInstance(0, 0, 0, 0))
                .doesNotThrowAnyException();

        Object properties = constructor.newInstance(0, 0, 0, 0);
        assertThat(propertiesType.getMethod("sessionIdleMinutes").invoke(properties)).isEqualTo(60);
        assertThat(propertiesType.getMethod("rawRetentionDays").invoke(properties)).isEqualTo(30);
        assertThat(propertiesType.getMethod("recentTurnLimit").invoke(properties)).isEqualTo(10);
        assertThat(propertiesType.getMethod("rollingSummaryTurnThreshold").invoke(properties)).isEqualTo(20);
    }
}
