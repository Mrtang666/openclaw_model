package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserCurrentStateWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserCurrentStateWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_current_state";
    }

    @Override
    public String description() {
        return "Inspect the current browser page URL, title, visible text summary, inputs, buttons, and login hints.";
    }

    @Override
    public List<String> arguments() {
        return List.of();
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of();
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Inspect current browser state before deciding the next browser action.",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.currentState(request.sessionKey()));
    }
}
