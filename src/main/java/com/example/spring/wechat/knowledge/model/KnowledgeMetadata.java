package com.example.spring.wechat.knowledge.model;

import java.util.List;

/**
 * 知识入库前的大模型元数据增强结果。
 */
public record KnowledgeMetadata(
        String title,
        String summary,
        List<String> tags) {

    public KnowledgeMetadata {
        title = title == null ? "" : title.strip();
        summary = summary == null ? "" : summary.strip();
        tags = tags == null
                ? List.of()
                : tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    public boolean isEmpty() {
        return title.isBlank() && summary.isBlank() && tags.isEmpty();
    }
}
