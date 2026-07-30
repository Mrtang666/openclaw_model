package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserTypeWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserTypeWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_type";
    }

    @Override
    public String description() {
        return "Type text into an input on the current browser page, including login form fields when the user explicitly requests it.";
    }

    @Override
    public List<String> arguments() {
        return List.of("target", "text");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("target", "Input description or CSS selector", "search-box"),
                WechatToolParameter.requiredString("text", "Ordinary text to type", "OpenClaw"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Type user-requested text into a page input, including credentials for a login flow.",
                List.of("Only type values explicitly provided by the user.", "Do not invent credentials or verification codes."),
                List.of("target: input field description.", "text: ordinary text."),
                List.of("Typing result."));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.type(
                request.sessionKey(),
                request.argument("target"),
                request.argument("text")));
    }
}
