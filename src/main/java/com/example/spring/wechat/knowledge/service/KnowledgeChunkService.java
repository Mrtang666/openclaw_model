package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.knowledge.model.KnowledgeTextChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把长文本切成适合向量检索的小片段。
 */
@Service
public class KnowledgeChunkService {

    private final KnowledgeProperties properties;

    public KnowledgeChunkService(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<KnowledgeTextChunk> split(String content) {
        String text = content == null ? "" : content.strip();
        if (text.isBlank()) {
            return List.of();
        }
        List<KnowledgeTextChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String paragraph : text.split("\\R{2,}")) {
            String value = paragraph.strip();
            if (value.isBlank()) {
                continue;
            }
            for (String part : splitParagraph(value)) {
                chunks.add(new KnowledgeTextChunk(index++, part));
            }
        }
        return chunks;
    }

    private List<String> splitParagraph(String paragraph) {
        if (paragraph.length() <= properties.chunkSize()) {
            return List.of(paragraph);
        }
        List<String> values = new ArrayList<>();
        int step = Math.max(1, properties.chunkSize() - properties.chunkOverlap());
        for (int start = 0; start < paragraph.length(); start += step) {
            int end = Math.min(paragraph.length(), start + properties.chunkSize());
            values.add(paragraph.substring(start, end).strip());
            if (end >= paragraph.length()) {
                break;
            }
        }
        return values;
    }
}
