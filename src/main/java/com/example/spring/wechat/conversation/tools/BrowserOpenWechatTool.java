package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserOpenWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserOpenWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_open";
    }

    @Override
    public String description() {
        return "Open an allowed web page for controlled browser automation and local web app testing.";
    }

    @Override
    public List<String> arguments() {
        return List.of("url");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.requiredString("url", "Full http or https URL to open", "http://localhost:8080"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Open a browser page.",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.open(request.sessionKey(), request.argument("url")));
    }
}
