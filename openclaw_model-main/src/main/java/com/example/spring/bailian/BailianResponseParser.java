package com.example.spring.bailian;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

public final class BailianResponseParser {
    private BailianResponseParser() {
    }

    public static String assistantText(JsonNode root) throws IOException {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual() && !content.asText().isBlank()) {
            return content.asText().trim();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : content) {
                String value = item.path("text").asText("").trim();
                if (!value.isEmpty()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(value);
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        throw new IOException("百炼响应中没有可用的模型回复");
    }
}
