package com.example.spring.wechat.conversation.tools;

import java.util.List;

/**
 * 微信工具能力边界说明。
 *
 * <p>它用于告诉大模型：工具能做什么、不能做什么、缺少哪些信息时要追问，以及执行后会产生什么输出。
 * 这些说明会被拼入 Function Calling 的工具描述中，降低模型误调用工具或参数不完整的概率。</p>
 */
public record WechatToolCapability(
        String summary,
        List<String> boundaries,
        List<String> requiredInformation,
        List<String> outputs) {

    public WechatToolCapability {
        summary = summary == null ? "" : summary.strip();
        boundaries = clean(boundaries);
        requiredInformation = clean(requiredInformation);
        outputs = clean(outputs);
    }

    public static WechatToolCapability empty() {
        return new WechatToolCapability("", List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return summary.isBlank()
                && boundaries.isEmpty()
                && requiredInformation.isEmpty()
                && outputs.isEmpty();
    }

    public String toPromptText() {
        if (isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        appendLine(text, "能力", summary);
        appendList(text, "边界", boundaries);
        appendList(text, "缺失信息处理", requiredInformation);
        appendList(text, "输出", outputs);
        return text.toString().strip();
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }

    private static void appendLine(StringBuilder text, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        appendSeparator(text);
        text.append(label).append("：").append(value.strip());
    }

    private static void appendList(StringBuilder text, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        appendSeparator(text);
        text.append(label).append("：").append(String.join("；", values));
    }

    private static void appendSeparator(StringBuilder text) {
        if (!text.isEmpty()) {
            text.append(System.lineSeparator());
        }
    }
}
