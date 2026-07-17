package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BrowserLauncherTests {
    @Test
    void acceptsWechatLoginHttpsUrl() {
        assertThat(
            BrowserLauncher.validatedHttpUri(
                "https://liteapp.weixin.qq.com/q/example?qrcode=test"))
            .hasScheme("https")
            .hasHost("liteapp.weixin.qq.com");
    }

    @Test
    void rejectsNonHttpLoginUrl() {
        assertThatThrownBy(() -> BrowserLauncher.validatedHttpUri("file:///secret"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
