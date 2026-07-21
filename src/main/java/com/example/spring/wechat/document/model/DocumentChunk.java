package com.example.spring.wechat.document.model;

/**
 * 文档解析后的一个文本片段。
 *
 * <p>分块是为了避免大文件直接塞进大模型。每个块可以对应一页、一个标题段落、
 * 一个工作表或一页幻灯片。</p>
 */
public record DocumentChunk(
        int index,
        String title,
        String text,
        String summary) {

    public DocumentChunk {
        title = title == null ? "" : title.strip();
        text = text == null ? "" : text.strip();
        summary = summary == null || summary.isBlank() ? buildSummary(text) : summary.strip();
    }

    private static String buildSummary(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }
}
