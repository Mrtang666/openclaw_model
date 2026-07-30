package com.example.spring.wechat.browser.service;

import com.example.spring.wechat.browser.client.BrowserMcpClient;
import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.web.exception.WebToolException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserAutomationService {

    private final BrowserMcpClient browserMcpClient;
    private final BrowserAutomationProperties properties;
    private final Map<String, PendingClick> pendingClicks = new ConcurrentHashMap<>();

    public BrowserAutomationService(BrowserMcpClient browserMcpClient, BrowserAutomationProperties properties) {
        this.browserMcpClient = browserMcpClient;
        this.properties = properties;
    }

    public String open(String userId, String url) {
        String validation = validateUrl(url);
        if (!validation.isBlank()) {
            return validation;
        }
        return format(browserMcpClient.open(url));
    }

    public String click(String userId, String target, String confirmToken) {
        String safeTarget = safe(target);
        if (safeTarget.isBlank()) {
            return "Tell me which page element to click.";
        }
        if (properties.requireConfirmationForRiskyActions() && isRiskyTarget(safeTarget)) {
            PendingClick pending = pendingClicks.get(confirmToken);
            if (confirmToken == null || confirmToken.isBlank() || pending == null
                    || !pending.userId().equals(safe(userId)) || !pending.target().equals(safeTarget)) {
                String token = UUID.randomUUID().toString();
                pendingClicks.put(token, new PendingClick(safe(userId), safeTarget));
                return "This click may have an external effect. Confirm before running it again with confirm_token="
                        + token + ".";
            }
            pendingClicks.remove(confirmToken);
        }
        return format(browserMcpClient.click(safeTarget));
    }

    public String type(String userId, String target, String text) {
        if (safe(target).isBlank()) {
            return "Tell me which input field to type into.";
        }
        if (safe(text).isBlank()) {
            return "Tell me what text to type.";
        }
        return format(browserMcpClient.type(target, text));
    }

    public String screenshot(String userId, String name) {
        return format(screenshotResult(userId, name));
    }

    public BrowserActionResult screenshotResult(String userId, String name) {
        return browserMcpClient.screenshot(name);
    }

    public String readPage(String userId, int maxChars) {
        int safeMax = maxChars <= 0 ? 2000 : Math.min(maxChars, 6000);
        return format(browserMcpClient.readPage(safeMax));
    }

    private String validateUrl(String rawUrl) {
        String value = safe(rawUrl);
        if (value.isBlank()) {
            return "Provide the URL to open.";
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "Browser automation only supports http or https URLs.";
            }
            if (!properties.allowExternalUrl() && !properties.allowedHosts().contains(host)) {
                return "Only configured allowed hosts can be opened now: "
                        + String.join(", ", properties.allowedHosts()) + ".";
            }
            return "";
        } catch (IllegalArgumentException exception) {
            return "The URL is invalid. Provide a full http or https address.";
        }
    }

    private boolean isRiskyTarget(String target) {
        String text = target.toLowerCase(Locale.ROOT);
        return text.contains("\u5220\u9664")
                || text.contains("\u652f\u4ed8")
                || text.contains("\u8d2d\u4e70")
                || text.contains("\u63d0\u4ea4")
                || text.contains("\u53d1\u9001")
                || text.contains("\u6388\u6743")
                || text.contains("delete")
                || text.contains("pay")
                || text.contains("buy")
                || text.contains("submit")
                || text.contains("send")
                || text.contains("authorize");
    }

    public String format(BrowserActionResult result) {
        try {
            StringBuilder text = new StringBuilder(result.message().isBlank() ? "Browser action completed." : result.message());
            if (!result.title().isBlank()) {
                text.append("\nTitle: ").append(result.title());
            }
            if (!result.url().isBlank()) {
                text.append("\nURL: ").append(result.url());
            }
            if (!result.screenshotPath().isBlank()) {
                text.append("\nScreenshot: ").append(result.screenshotPath());
            }
            return text.toString();
        } catch (RuntimeException exception) {
            throw new WebToolException("Failed to parse browser automation result", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private record PendingClick(String userId, String target) {
    }
}
