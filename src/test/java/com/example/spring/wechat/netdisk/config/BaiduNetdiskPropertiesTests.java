package com.example.spring.wechat.netdisk.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduNetdiskPropertiesTests {

    @Test
    void appliesSafeDefaultsForEmptyConfiguration() {
        BaiduNetdiskProperties properties = new BaiduNetdiskProperties(
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                0,
                0,
                "");

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.appId()).isEmpty();
        assertThat(properties.appKey()).isEmpty();
        assertThat(properties.oauthClientId()).isEmpty();
        assertThat(properties.secretKey()).isEmpty();
        assertThat(properties.signKey()).isEmpty();
        assertThat(properties.redirectUri()).isEmpty();
        assertThat(properties.authBaseUrl()).isEmpty();
        assertThat(properties.tokenUrl()).isEmpty();
        assertThat(properties.mcpSseBaseUrl()).isEqualTo("https://mcp-pan.baidu.com/sse");
        assertThat(properties.tokenEncryptionKey()).isEmpty();
        assertThat(properties.authStateTtlMinutes()).isEqualTo(10);
        assertThat(properties.pendingActionTtlMinutes()).isEqualTo(30);
        assertThat(properties.mcpTimeoutMs()).isEqualTo(20_000);
        assertThat(properties.contextLimit()).isEqualTo(5);
        assertThat(properties.defaultUploadPath()).isEqualTo("/OpenClaw/");
    }
}
