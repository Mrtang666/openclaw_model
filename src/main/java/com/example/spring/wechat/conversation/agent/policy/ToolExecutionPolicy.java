package com.example.spring.wechat.conversation.agent.policy;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.conversation.agent.FunctionCallingAgentRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ToolExecutionPolicy {

    public boolean shouldSkipWebSearchBecauseRagHasEvidence(
            FunctionCallingAgentRequest request,
            FunctionCallingToolCall toolCall,
            Map<String, String> arguments) {
        if (request == null || toolCall == null || !"web_search".equals(toolCall.name())) {
            return false;
        }
        if (request.ragContext().isBlank()) {
            return false;
        }
        return !requiresFreshWebSearch(request.userText(), arguments);
    }

    public String voiceSynthesisSignature(String toolName, Map<String, String> arguments) {
        if (!"voice_synthesis".equals(toolName) || arguments == null || arguments.isEmpty()) {
            return "";
        }
        String target = firstNonBlank(
                arguments.get("target_text"),
                arguments.get("text"),
                arguments.get("message"),
                arguments.get("previous_result"));
        if (target.isBlank()) {
            return "";
        }
        String voice = firstNonBlank(arguments.get("voice")).toLowerCase(Locale.ROOT);
        return toolName + "|" + voice + "|" + normalizeForSignature(target);
    }

    public String toolCallSignature(String toolName, Map<String, String> arguments) {
        return firstNonBlank(toolName) + "|" + normalizedArgumentString(arguments);
    }

    public String toolFailureSignature(String toolName, Map<String, String> arguments, String errorMessage) {
        return firstNonBlank(toolName)
                + "|"
                + normalizedArgumentString(arguments)
                + "|"
                + normalizeForSignature(errorMessage);
    }

    private boolean requiresFreshWebSearch(String userText, Map<String, String> arguments) {
        StringBuilder text = new StringBuilder(firstNonBlank(userText).toLowerCase(Locale.ROOT));
        if (arguments != null && !arguments.isEmpty()) {
            for (String value : arguments.values()) {
                if (value != null && !value.isBlank()) {
                    text.append(' ').append(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return containsAny(text.toString(),
                "\u6700\u65b0", "\u6700\u8fd1", "\u4eca\u5929", "\u73b0\u5728", "\u5f53\u524d", "\u5b9e\u65f6",
                "\u8054\u7f51", "\u4e92\u8054\u7f51", "\u7f51\u9875", "\u5b98\u7f51", "\u65b0\u95fb",
                "\u4ef7\u683c", "\u641c\u7d22", "\u516c\u5f00\u8d44\u6599",
                "latest", "current", "today", "recent", "web", "internet",
                "official", "news", "price");
    }

    private boolean containsAny(String text, String... markers) {
        if (text == null || text.isBlank() || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (marker != null && !marker.isBlank() && text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String normalizedArgumentString(Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        Map<String, String> normalized = new TreeMap<>();
        arguments.forEach((key, value) -> normalized.put(firstNonBlank(key), normalizeForSignature(value)));
        return normalized.toString();
    }

    private String normalizeForSignature(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }
}
