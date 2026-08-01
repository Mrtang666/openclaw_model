package com.example.spring.wechat.browser.service;

import com.example.spring.wechat.browser.client.BrowserMcpClient;
import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.web.exception.WebToolException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserAutomationService {

    private static final Set<String> WAIT_CONDITIONS = Set.of("url", "title", "text", "selector");

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

    public String currentState(String userId) {
        return format(browserMcpClient.currentState());
    }

    public String waitFor(String userId, String condition, String value, int timeoutMs) {
        String safeCondition = safe(condition).toLowerCase(Locale.ROOT);
        String safeValue = safe(value);
        if (!WAIT_CONDITIONS.contains(safeCondition)) {
            return "Wait condition must be one of: " + String.join(", ", WAIT_CONDITIONS) + ".";
        }
        if (safeValue.isBlank()) {
            return "Tell me what browser state value to wait for.";
        }
        int safeTimeoutMs = timeoutMs <= 0 ? 15_000 : Math.min(Math.max(timeoutMs, 1_000), 60_000);
        return format(browserMcpClient.waitFor(safeCondition, safeValue, safeTimeoutMs));
    }

    public String reset(String userId, boolean clearProfile) {
        return format(browserMcpClient.reset(clearProfile));
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
            appendTextField(text, result.raw(), "pageText", "Page text");
            appendArrayField(text, result.raw(), "inputs", "Inputs");
            appendArrayField(text, result.raw(), "buttons", "Buttons");
            appendBooleanHints(text, result.raw());
            return text.toString();
        } catch (RuntimeException exception) {
            throw new WebToolException("Failed to parse browser automation result", exception);
        }
    }

    private void appendTextField(StringBuilder text, JsonNode raw, String field, String label) {
        String value = firstRaw(raw, field).asText("");
        if (!value.isBlank()) {
            text.append("\n").append(label).append(": ").append(value);
        }
    }

    private void appendArrayField(StringBuilder text, JsonNode raw, String field, String label) {
        JsonNode values = firstRaw(raw, field);
        if (!values.isArray() || values.isEmpty()) {
            return;
        }
        text.append("\n").append(label).append(":");
        int count = 0;
        for (JsonNode value : values) {
            String item = value.isTextual() ? value.asText("") : compactObject(value);
            if (!item.isBlank()) {
                text.append("\n- ").append(item);
                count++;
            }
            if (count >= 20) {
                break;
            }
        }
    }

    private void appendBooleanHints(StringBuilder text, JsonNode raw) {
        JsonNode isLoginPage = firstRaw(raw, "isLoginPage");
        JsonNode requiresVerification = firstRaw(raw, "requiresVerification");
        if (!isLoginPage.isMissingNode() || !requiresVerification.isMissingNode()) {
            text.append("\nPage hints: login=")
                    .append(isLoginPage.asBoolean(false))
                    .append(", verification=")
                    .append(requiresVerification.asBoolean(false));
        }
    }

    private JsonNode firstRaw(JsonNode raw, String field) {
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        JsonNode structured = raw.path("structuredContent").path(field);
        return structured.isMissingNode() ? raw.path(field) : structured;
    }

    private String compactObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isObject()) {
            return node.asText("");
        }
        return List.of("label", "name", "id", "type", "text", "placeholder")
                .stream()
                .map(field -> node.path(field).asText(""))
                .filter(value -> !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + " | " + right)
                .orElse(node.toString());
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private record PendingClick(String userId, String target) {
    }
}
