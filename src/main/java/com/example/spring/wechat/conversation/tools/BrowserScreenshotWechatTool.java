package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserScreenshotWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserScreenshotWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_screenshot";
    }

    @Override
    public String description() {
        return "Take a screenshot of the current browser page.";
    }

    @Override
    public List<String> arguments() {
        return List.of("name");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalString("name", "Screenshot file name hint", "home-page"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.screenshot(request.sessionKey(), request.argument("name")));
    }
}
