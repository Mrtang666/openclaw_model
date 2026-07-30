package com.example.spring.wechat.browser.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public record BrowserActionResult(
        boolean success,
        String message,
        String title,
        String url,
        String screenshotPath,
        JsonNode raw) {

    public BrowserActionResult {
        message = message == null ? "" : message.strip();
        title = title == null ? "" : title.strip();
        url = url == null ? "" : url.strip();
        screenshotPath = screenshotPath == null ? "" : screenshotPath.strip();
    }

    public static BrowserActionResult from(JsonNode node) {
        JsonNode safe = node == null ? MissingNode.getInstance() : node;
        JsonNode structured = safe.path("structuredContent");
        JsonNode source = structured.isMissingNode() || structured.isNull() ? safe : structured;
        return new BrowserActionResult(
                source.path("success").asBoolean(true),
                source.path("message").asText("Browser action completed"),
                source.path("title").asText(""),
                source.path("url").asText(""),
                source.path("screenshotPath").asText(""),
                safe);
    }
}
