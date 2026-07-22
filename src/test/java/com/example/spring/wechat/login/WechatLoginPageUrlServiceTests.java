package com.example.spring.wechat.login;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WechatLoginPageUrlServiceTests {

    @Test
    void buildsConfiguredLoginPageUrl() {
        WechatLoginPageProperties properties = new WechatLoginPageProperties();
        properties.setBaseUrl("https://login.example.com/");
        WechatLoginPageUrlService service = new WechatLoginPageUrlService(
                properties,
                mock(ApplicationContext.class));

        assertThat(service.pageUrl("session id"))
                .isEqualTo("https://login.example.com/wechat-login/index.html?session=session+id");
    }
}
