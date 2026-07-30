package com.example.spring.wechat.browser.service;

import com.example.spring.wechat.browser.client.BrowserMcpClient;
import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAutomationServiceTests {

    @Test
    void rejectsExternalUrlWhenExternalAccessDisabled() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.open("wx-user-1", "https://example.com");

        assertThat(result).contains("Only configured allowed hosts");
        assertThat(client.openedUrl).isNull();
    }

    @Test
    void opensAllowedLocalhostUrl() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.open("wx-user-1", "http://localhost:8080");

        assertThat(client.openedUrl).isEqualTo("http://localhost:8080");
        assertThat(result).contains("Home").contains("http://localhost:8080");
    }

    @Test
    void riskyClickRequiresConfirmationBeforeCallingMcp() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String first = service.click("wx-user-1", "delete order", "");

        assertThat(first).contains("Confirm").contains("confirm_token");
        assertThat(client.clickedTarget).isNull();
    }

    @Test
    void chineseRiskyClickRequiresConfirmationBeforeCallingMcp() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String first = service.click("wx-user-1", "\u5220\u9664\u8ba2\u5355", "");

        assertThat(first).contains("Confirm").contains("confirm_token");
        assertThat(client.clickedTarget).isNull();
    }

    @Test
    void allowsPasswordInputForUserDrivenLogin() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.type("wx-user-1", "password", "123456");

        assertThat(result).contains("Typed");
        assertThat(client.typedTarget).isEqualTo("password");
        assertThat(client.typedText).isEqualTo("123456");
    }

    @Test
    void allowsVerificationCodeInputForUserDrivenLogin() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.type("wx-user-1", "code", "123456");

        assertThat(result).contains("Typed");
        assertThat(client.typedTarget).isEqualTo("code");
        assertThat(client.typedText).isEqualTo("123456");
    }

    @Test
    void loginClickDoesNotRequireConfirmation() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.click("wx-user-1", "login", "");

        assertThat(result).contains("Clicked");
        assertThat(client.clickedTarget).isEqualTo("login");
    }

    @Test
    void chineseLoginClickDoesNotRequireConfirmation() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.click("wx-user-1", "\u767b\u5f55", "");

        assertThat(result).contains("Clicked");
        assertThat(client.clickedTarget).isEqualTo("\u767b\u5f55");
    }

    private BrowserAutomationProperties localOnlyProperties() {
        return new BrowserAutomationProperties(
                true,
                "http://127.0.0.1:3333/mcp",
                "",
                30_000,
                false,
                "localhost,127.0.0.1",
                "data/browser/screenshots",
                true);
    }

    private static final class RecordingBrowserMcpClient extends BrowserMcpClient {
        private String openedUrl;
        private String clickedTarget;
        private String typedTarget;
        private String typedText;

        private RecordingBrowserMcpClient() {
            super(null, null);
        }

        @Override
        public BrowserActionResult open(String url) {
            this.openedUrl = url;
            return result("Opened page", "Home", url, "");
        }

        @Override
        public BrowserActionResult click(String target) {
            this.clickedTarget = target;
            return result("Clicked", "", "", "");
        }

        @Override
        public BrowserActionResult type(String target, String text) {
            this.typedTarget = target;
            this.typedText = text;
            return result("Typed", "", "", "");
        }

        @Override
        public BrowserActionResult screenshot(String name) {
            return result("Screenshot captured", "", "", "data/browser/screenshots/test.png");
        }

        @Override
        public BrowserActionResult readPage(int maxChars) {
            return result("Page text", "", "", "");
        }

        private BrowserActionResult result(String message, String title, String url, String screenshotPath) {
            return new BrowserActionResult(true, message, title, url, screenshotPath, JsonNodeFactory.instance.objectNode());
        }
    }
}
