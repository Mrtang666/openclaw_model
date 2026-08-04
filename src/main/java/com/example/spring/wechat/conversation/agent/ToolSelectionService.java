package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ToolSelectionService {

    private final boolean enabled;
    private final int maxSelectedTools;

    public ToolSelectionService(
            @Value("${agent.tool-calling.dynamic-tool-selection-enabled:true}") boolean enabled,
            @Value("${agent.tool-calling.max-selected-tools:6}") int maxSelectedTools) {
        this.enabled = enabled;
        this.maxSelectedTools = Math.max(1, maxSelectedTools);
    }

    public List<WechatToolDefinition> select(
            FunctionCallingAgentRequest request,
            List<WechatToolDefinition> definitions) {
        if (!enabled || definitions == null || definitions.isEmpty() || definitions.size() <= maxSelectedTools) {
            return definitions == null ? List.of() : definitions;
        }

        String query = queryText(request);
        Map<WechatToolDefinition, Integer> scored = new LinkedHashMap<>();
        for (WechatToolDefinition definition : definitions) {
            if (definition == null || definition.name().isBlank()) {
                continue;
            }
            int score = score(definition, query, request);
            if (score > 0) {
                scored.put(definition, score);
            }
        }

        if (scored.isEmpty()) {
            return definitions.stream()
                    .filter(definition -> definition != null && !definition.name().isBlank())
                    .limit(maxSelectedTools)
                    .toList();
        }

        return scored.entrySet().stream()
                .sorted(Map.Entry.<WechatToolDefinition, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().name()))
                .limit(maxSelectedTools)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int score(WechatToolDefinition definition, String query, FunctionCallingAgentRequest request) {
        String name = normalize(definition.name());
        String haystack = normalize(definition.name() + " " + definition.description() + " "
                + String.join(" ", definition.parameters().stream()
                .map(WechatToolParameter::name)
                .toList()));
        int score = 0;
        if (containsAny(query, "天气", "气温", "下雨", "雨", "雪", "weather", "forecast")) {
            score += containsAny(name, "weather") || containsAny(haystack, "weather", "天气") ? 100 : 0;
        }
        if (containsAny(query, "图片", "图", "画", "生成图", "image", "photo", "头像")) {
            score += containsAny(name, "image") || containsAny(haystack, "image", "图片") ? 100 : 0;
        }
        if (containsAny(query, "视频", "video")) {
            score += containsAny(name, "video") || containsAny(haystack, "video", "视频") ? 100 : 0;
        }
        if (containsAny(query, "文件", "文档", "pdf", "docx", "报告", "周报", "document", "file")) {
            score += containsAny(name, "document", "file", "pdf") || containsAny(haystack, "document", "file", "文档", "文件") ? 100 : 0;
        }
        if (containsAny(query, "邮件", "邮箱", "email", "mail", "发送给")) {
            score += containsAny(name, "email", "mail") || containsAny(haystack, "email", "mail", "邮件") ? 100 : 0;
        }
        if (containsAny(query, "搜索", "查一下", "最新", "网页", "联网", "web", "search", "read")) {
            score += containsAny(name, "web") || containsAny(haystack, "web", "search", "搜索", "网页") ? 100 : 0;
        }
        if (containsAny(query, "提醒", "闹钟", "定时", "reminder")) {
            score += containsAny(name, "reminder") || containsAny(haystack, "reminder", "提醒") ? 100 : 0;
        }
        if (containsAny(query, "打车", "出租车", "滴滴", "taxi")) {
            score += containsAny(name, "taxi") || containsAny(haystack, "taxi", "滴滴", "打车") ? 100 : 0;
        }
        if (request != null) {
            if (!request.images().isEmpty()) {
                score += containsAny(name, "image", "vision") || containsAny(haystack, "image", "vision", "图片") ? 120 : 0;
            }
            if (!request.files().isEmpty()) {
                score += containsAny(name, "document", "file", "pdf", "email") || containsAny(haystack, "document", "file", "文件") ? 120 : 0;
            }
            if (!request.videos().isEmpty()) {
                score += containsAny(name, "video") || containsAny(haystack, "video", "视频") ? 120 : 0;
            }
            if (!request.ragContext().isBlank()) {
                score += containsAny(name, "web") ? 10 : 0;
            }
        }
        if (containsAny(haystack, query) || containsAny(query, name)) {
            score += 20;
        }
        return score;
    }

    private String queryText(FunctionCallingAgentRequest request) {
        if (request == null) {
            return "";
        }
        return normalize(request.userText() + " " + request.historyText() + " " + request.ragContext());
    }

    private boolean containsAny(String text, String... markers) {
        if (text == null || text.isBlank() || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (marker != null && !marker.isBlank() && text.contains(normalize(marker))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
