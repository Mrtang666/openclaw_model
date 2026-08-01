package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserResetWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserResetWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_reset";
    }

    @Override
    public String description() {
        return "Restart the managed browser automation session, optionally clearing the Chrome profile.";
    }

    @Override
    public List<String> arguments() {
        return List.of("clear_profile");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalBoolean(
                "clear_profile",
                "Whether to clear the Chrome profile and login state during reset",
                false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "Recover from a stale browser target, locked profile, or broken browser session.",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.reset(
                request.sessionKey(),
                request.booleanArgument("clear_profile")));
    }
}
