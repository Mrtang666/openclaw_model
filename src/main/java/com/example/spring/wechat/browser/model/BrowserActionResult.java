package com.example.spring.wechat.browser.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public record BrowserActionResult(
        boolean success,
        String message,
        String title,
        String url,
        String screenshotPath,
        String screenshotImageBase64,
        String screenshotContentType,
        String screenshotFileName,
        JsonNode raw) {

    public BrowserActionResult(
            boolean success,
            String message,
            String title,
            String url,
            String screenshotPath,
            JsonNode raw) {
        this(success, message, title, url, screenshotPath, "", "", "", raw);
    }

    public BrowserActionResult {
        message = message == null ? "" : message.strip();
        title = title == null ? "" : title.strip();
        url = url == null ? "" : url.strip();
        screenshotPath = screenshotPath == null ? "" : screenshotPath.strip();
        screenshotImageBase64 = screenshotImageBase64 == null ? "" : screenshotImageBase64.strip();
        screenshotContentType = screenshotContentType == null ? "" : screenshotContentType.strip();
        screenshotFileName = screenshotFileName == null ? "" : screenshotFileName.strip();
    }

    public static BrowserActionResult from(JsonNode node) {
        JsonNode safe = node == null ? MissingNode.getInstance() : node;
        JsonNode structured = safe.path("structuredContent");
        JsonNode source = structured.isMissingNode() || structured.isNull() ? safe : structured;
        String message = firstText(source, "message");
        if (message.isBlank()) {
            message = firstContentText(safe);
        }
        return new BrowserActionResult(
                source.path("success").asBoolean(!safe.path("isError").asBoolean(false)),
                message.isBlank() ? "Browser action completed" : message,
                source.path("title").asText(""),
                source.path("url").asText(""),
                source.path("screenshotPath").asText(""),
                firstText(source, "screenshotImageBase64", "imageBase64"),
                firstText(source, "screenshotContentType", "imageContentType", "contentType"),
                firstText(source, "screenshotFileName", "imageFileName", "fileName"),
                safe);
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstContentText(JsonNode node) {
        JsonNode content = node.path("content");
        if (!content.isArray()) {
            return "";
        }
        for (JsonNode item : content) {
            String text = item.path("text").asText("");
            if (!text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }
}
