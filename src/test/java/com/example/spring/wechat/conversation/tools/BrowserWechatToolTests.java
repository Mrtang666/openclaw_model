package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserWechatToolTests {

    @Test
    void openToolDelegatesUrl() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserOpenWechatTool tool = new BrowserOpenWechatTool(service);

        var reply = tool.execute(request(Map.of("url", "http://localhost:8080")));

        assertThat(tool.name()).isEqualTo("browser_open");
        assertThat(reply.text()).isEqualTo("open-result");
        assertThat(service.url).isEqualTo("http://localhost:8080");
        assertThat(service.userId).isEqualTo("wx-user-1");
    }

    @Test
    void clickToolPassesConfirmToken() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserClickWechatTool tool = new BrowserClickWechatTool(service);

        tool.execute(request(Map.of("target", "delete", "confirm_token", "token-1")));

        assertThat(service.target).isEqualTo("delete");
        assertThat(service.confirmToken).isEqualTo("token-1");
    }

    @Test
    void typeToolPassesTargetAndText() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserTypeWechatTool tool = new BrowserTypeWechatTool(service);

        tool.execute(request(Map.of("target", "search-box", "text", "OpenClaw")));

        assertThat(service.target).isEqualTo("search-box");
        assertThat(service.text).isEqualTo("OpenClaw");
    }

    @Test
    void screenshotToolPassesName() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserScreenshotWechatTool tool = new BrowserScreenshotWechatTool(service);

        var reply = tool.execute(request(Map.of("name", "home")));

        assertThat(tool.name()).isEqualTo("browser_screenshot");
        assertThat(reply.text()).isEqualTo("screenshot-result");
        assertThat(service.userId).isEqualTo("wx-user-1");
        assertThat(service.screenshotName).isEqualTo("home");
    }

    @Test
    void readPageToolParsesMaxChars() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserReadPageWechatTool tool = new BrowserReadPageWechatTool(service);

        var reply = tool.execute(request(Map.of("max_chars", "3500")));

        assertThat(tool.name()).isEqualTo("browser_read_page");
        assertThat(reply.text()).isEqualTo("read-result");
        assertThat(service.userId).isEqualTo("wx-user-1");
        assertThat(service.maxChars).isEqualTo(3500);
    }

    @Test
    void readPageToolFallsBackForInvalidMaxChars() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserReadPageWechatTool tool = new BrowserReadPageWechatTool(service);

        tool.execute(request(Map.of("max_chars", "many")));

        assertThat(service.maxChars).isEqualTo(2000);
    }

    private WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("wx-user-1", "user request", arguments, "", null, null);
    }

    private static final class RecordingBrowserAutomationService extends BrowserAutomationService {
        private String userId;
        private String url;
        private String target;
        private String text;
        private String confirmToken;
        private String screenshotName;
        private int maxChars;

        private RecordingBrowserAutomationService() {
            super(null, null);
        }

        @Override
        public String open(String userId, String url) {
            this.userId = userId;
            this.url = url;
            return "open-result";
        }

        @Override
        public String click(String userId, String target, String confirmToken) {
            this.userId = userId;
            this.target = target;
            this.confirmToken = confirmToken;
            return "click-result";
        }

        @Override
        public String type(String userId, String target, String text) {
            this.userId = userId;
            this.target = target;
            this.text = text;
            return "type-result";
        }

        @Override
        public String screenshot(String userId, String name) {
            this.userId = userId;
            this.screenshotName = name;
            return "screenshot-result";
        }

        @Override
        public String readPage(String userId, int maxChars) {
            this.userId = userId;
            this.maxChars = maxChars;
            return "read-result";
        }
    }
}
