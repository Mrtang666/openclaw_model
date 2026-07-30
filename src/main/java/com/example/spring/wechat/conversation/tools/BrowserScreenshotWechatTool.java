package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

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
        BrowserActionResult result = browserAutomationService.screenshotResult(request.sessionKey(), request.argument("name"));
        String caption = browserAutomationService.format(result);
        byte[] imageBytes = screenshotBytes(result.screenshotImageBase64());
        if (imageBytes.length == 0) {
            return WechatReply.text(caption);
        }
        ImageGenerationResult image = new ImageGenerationResult(
                "Browser screenshot",
                "",
                imageBytes,
                screenshotFileName(result),
                screenshotContentType(result),
                null,
                null);
        return WechatReply.ordered(List.of(WechatReply.Part.image(caption, image)));
    }

    private byte[] screenshotBytes(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return new byte[0];
        }
        String value = imageBase64.strip();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) {
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    private String screenshotFileName(BrowserActionResult result) {
        if (!result.screenshotFileName().isBlank()) {
            return result.screenshotFileName();
        }
        String path = result.screenshotPath();
        if (!path.isBlank()) {
            String normalized = path.replace('\\', '/');
            int index = normalized.lastIndexOf('/');
            String fileName = index >= 0 ? normalized.substring(index + 1) : normalized;
            if (!fileName.isBlank()) {
                return fileName;
            }
        }
        return "browser-screenshot.png";
    }

    private String screenshotContentType(BrowserActionResult result) {
        String contentType = result.screenshotContentType();
        if (!contentType.isBlank()) {
            return contentType;
        }
        String fileName = screenshotFileName(result).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "image/png";
    }
}
