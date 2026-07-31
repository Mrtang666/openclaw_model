package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserWaitForWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserWaitForWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_wait_for";
    }

    @Override
    public String description() {
        return "Wait until the current browser page URL, title, text, or CSS selector reaches an expected state.";
    }

    @Override
    public List<String> arguments() {
        return List.of("condition", "value", "timeout_ms");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "condition",
                        "State type to wait for",
                        List.of("url", "title", "text", "selector"),
                        "url"),
                WechatToolParameter.requiredString("value", "Expected value or CSS selector", "dashboard"),
                WechatToolParameter.optionalString("timeout_ms", "Maximum wait time in milliseconds, default 15000", "15000"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Wait for navigation, login completion, rendered text, or an element before continuing.",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.waitFor(
                request.sessionKey(),
                request.argument("condition"),
                request.argument("value"),
                parseInt(request.argument("timeout_ms"), 15_000)));
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
