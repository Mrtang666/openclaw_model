package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
    void screenshotToolReturnsWechatImageWhenScreenshotBytesArePresent() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        service.screenshotResult = new BrowserActionResult(
                true,
                "Screenshot captured",
                "Dashboard",
                "http://localhost:8080/dashboard",
                "data/browser/screenshots/dashboard.png",
                "AQID",
                "image/png",
                "dashboard.png",
                JsonNodeFactory.instance.objectNode());
        BrowserScreenshotWechatTool tool = new BrowserScreenshotWechatTool(service);

        var reply = tool.execute(request(Map.of("name", "dashboard")));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasImage()).isTrue();
        assertThat(reply.parts().get(0).image().imageBytes()).containsExactly(1, 2, 3);
        assertThat(reply.parts().get(0).image().fileName()).isEqualTo("dashboard.png");
        assertThat(reply.parts().get(0).image().contentType()).isEqualTo("image/png");
        assertThat(reply.parts().get(0).text()).contains("Screenshot captured", "dashboard.png");
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

    @Test
    void currentStateToolDelegates() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserCurrentStateWechatTool tool = new BrowserCurrentStateWechatTool(service);

        var reply = tool.execute(request(Map.of()));

        assertThat(tool.name()).isEqualTo("browser_current_state");
        assertThat(reply.text()).isEqualTo("state-result");
        assertThat(service.userId).isEqualTo("wx-user-1");
    }

    @Test
    void waitForToolPassesArguments() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserWaitForWechatTool tool = new BrowserWaitForWechatTool(service);

        var reply = tool.execute(request(Map.of(
                "condition", "url",
                "value", "dashboard",
                "timeout_ms", "12000")));

        assertThat(tool.name()).isEqualTo("browser_wait_for");
        assertThat(reply.text()).isEqualTo("wait-result");
        assertThat(service.waitCondition).isEqualTo("url");
        assertThat(service.waitValue).isEqualTo("dashboard");
        assertThat(service.waitTimeoutMs).isEqualTo(12000);
    }

    @Test
    void resetToolPassesClearProfileFlag() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserResetWechatTool tool = new BrowserResetWechatTool(service);

        var reply = tool.execute(request(Map.of("clear_profile", "true")));

        assertThat(tool.name()).isEqualTo("browser_reset");
        assertThat(reply.text()).isEqualTo("reset-result");
        assertThat(service.resetClearProfile).isTrue();
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
        private BrowserActionResult screenshotResult;
        private String waitCondition;
        private String waitValue;
        private int waitTimeoutMs;
        private boolean resetClearProfile;

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
        public BrowserActionResult screenshotResult(String userId, String name) {
            this.userId = userId;
            this.screenshotName = name;
            return screenshotResult == null
                    ? new BrowserActionResult(true, "screenshot-result", "", "", "", "", "", "", JsonNodeFactory.instance.objectNode())
                    : screenshotResult;
        }

        @Override
        public String readPage(String userId, int maxChars) {
            this.userId = userId;
            this.maxChars = maxChars;
            return "read-result";
        }

        @Override
        public String currentState(String userId) {
            this.userId = userId;
            return "state-result";
        }

        @Override
        public String waitFor(String userId, String condition, String value, int timeoutMs) {
            this.userId = userId;
            this.waitCondition = condition;
            this.waitValue = value;
            this.waitTimeoutMs = timeoutMs;
            return "wait-result";
        }

        @Override
        public String reset(String userId, boolean clearProfile) {
            this.userId = userId;
            this.resetClearProfile = clearProfile;
            return "reset-result";
        }
    }
}
