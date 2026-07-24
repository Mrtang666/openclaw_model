package com.example.spring.wechat.knowledge.service;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.knowledge.model.KnowledgeMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识入库元数据增强器。
 *
 * <p>对长内容、网页和文档使用大模型生成更清晰的标题、摘要和标签。
 * 如果模型失败或返回格式不合法，会回退为空结果，不影响原始入库流程。</p>
 */
@Service
public class KnowledgeMetadataEnhancer {

    private static final int LONG_CONTENT_THRESHOLD = 500;

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeMetadataEnhancer(ChatService chatService) {
        this.chatService = chatService;
    }

    public boolean shouldEnhance(String sourceType, String content) {
        String type = sourceType == null ? "" : sourceType.strip().toLowerCase(java.util.Locale.ROOT);
        String text = content == null ? "" : content.strip();
        return "web".equals(type)
                || "document".equals(type)
                || "file".equals(type)
                || text.length() >= LONG_CONTENT_THRESHOLD;
    }

    public KnowledgeMetadata enhance(String title, String content, String sourceType, String sourceUrl) {
        if (!shouldEnhance(sourceType, content) || chatService == null) {
            return new KnowledgeMetadata("", "", List.of());
        }
        try {
            String reply = chatService.reply(prompt(title, content, sourceType, sourceUrl));
            return parse(reply);
        } catch (RuntimeException exception) {
            return new KnowledgeMetadata("", "", List.of());
        }
    }

    private KnowledgeMetadata parse(String reply) {
        String json = extractJson(reply);
        if (json.isBlank()) {
            return new KnowledgeMetadata("", "", List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> tags = new ArrayList<>();
            JsonNode tagNode = root.path("tags");
            if (tagNode.isArray()) {
                tagNode.forEach(value -> tags.add(value.asText("")));
            } else if (tagNode.isTextual()) {
                tags.addAll(List.of(tagNode.asText("").split("[,，;；]")));
            }
            return new KnowledgeMetadata(
                    root.path("title").asText(""),
                    root.path("summary").asText(""),
                    tags);
        } catch (Exception exception) {
            return new KnowledgeMetadata("", "", List.of());
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return value.substring(start, end + 1);
    }

    private String prompt(String title, String content, String sourceType, String sourceUrl) {
        return """
                你是知识库资料整理助手。请根据资料内容生成更适合检索的元数据。
                只返回 JSON，不要解释。
                JSON 格式：
                {"title":"清晰标题","summary":"100字以内摘要","tags":["标签1","标签2","标签3"]}

                原标题：%s
                来源类型：%s
                来源链接：%s
                内容：
                %s
                """.formatted(
                safe(title),
                safe(sourceType),
                safe(sourceUrl),
                truncate(content, 4000));
    }

    private String truncate(String value, int maxLength) {
        String text = safe(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
