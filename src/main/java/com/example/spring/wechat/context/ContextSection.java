package com.example.spring.wechat.context;

public record ContextSection(
        String key,
        String title,
        String content,
        int compressionPriority,
        boolean compressible) {

    public ContextSection {
        key = key == null ? "" : key.strip();
        title = title == null ? "" : title.strip();
        content = content == null ? "" : content.strip();
    }

    public String formatted() {
        if (content.isBlank()) {
            return "";
        }
        return "【" + title + "】" + System.lineSeparator() + content;
    }

    public ContextSection withContent(String newContent) {
        return new ContextSection(key, title, newContent, compressionPriority, compressible);
    }
}
