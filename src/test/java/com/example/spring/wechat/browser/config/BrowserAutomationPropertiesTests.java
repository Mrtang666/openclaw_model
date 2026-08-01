package com.example.spring.wechat.browser.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAutomationPropertiesTests {

    @Test
    void appliesSafeDefaultsForEmptyConfiguration() {
        BrowserAutomationProperties properties = new BrowserAutomationProperties(
                false,
                "",
                "",
                0,
                false,
                "",
                "",
                false);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.mcpEndpoint()).isEqualTo("http://127.0.0.1:3333/mcp");
        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.timeoutMs()).isEqualTo(30_000);
        assertThat(properties.allowExternalUrl()).isFalse();
        assertThat(properties.allowedHosts()).containsExactly("localhost", "127.0.0.1");
        assertThat(properties.screenshotDir()).isEqualTo("data/browser/screenshots");
        assertThat(properties.requireConfirmationForRiskyActions()).isFalse();
    }

    @Test
    void trimsAndDeduplicatesAllowedHosts() {
        BrowserAutomationProperties properties = new BrowserAutomationProperties(
                true,
                " http://browser-mcp-sidecar:3333/mcp ",
                " key-1 ",
                15_000,
                true,
                " localhost, example.com, localhost , 127.0.0.1 ",
                " data/custom ",
                true);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.mcpEndpoint()).isEqualTo("http://browser-mcp-sidecar:3333/mcp");
        assertThat(properties.apiKey()).isEqualTo("key-1");
        assertThat(properties.timeoutMs()).isEqualTo(15_000);
        assertThat(properties.allowExternalUrl()).isTrue();
        assertThat(properties.allowedHosts()).containsExactly("localhost", "example.com", "127.0.0.1");
        assertThat(properties.screenshotDir()).isEqualTo("data/custom");
        assertThat(properties.requireConfirmationForRiskyActions()).isTrue();
    }
}
