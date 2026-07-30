package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserClickWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserClickWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_click";
    }

    @Override
    public String description() {
        return "Click an element on the current browser page by visible text, description, or CSS selector.";
    }

    @Override
    public List<String> arguments() {
        return List.of("target", "confirm_token");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("target", "Element text, description, or CSS selector to click", "submit button"),
                WechatToolParameter.optionalString("confirm_token", "Confirmation token for risky clicks", "uuid"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Click a page element.",
                List.of("Risky clicks such as delete, pay, submit, send, authorize, or login require confirmation."),
                List.of("target: page element description."),
                List.of("Click result or confirmation prompt."));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.click(
                request.sessionKey(),
                request.argument("target"),
                request.argument("confirm_token")));
    }
}
