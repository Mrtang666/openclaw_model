package com.example.spring.wechat.document.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将长文本切成适合摘要和检索的片段。
 */
@Component
public class DocumentChunkService {

    private final int maxChunkSize;

    public DocumentChunkService() {
        this(1200);
    }

    public DocumentChunkService(int maxChunkSize) {
        this.maxChunkSize = Math.max(20, maxChunkSize);
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\R\\s*\\R");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String value = paragraph == null ? "" : paragraph.strip();
            if (value.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + value.length() + 2 >= Math.max(1, (int) (maxChunkSize * 0.8))) {
                chunks.add(current.toString().strip());
                current.setLength(0);
            }
            if (value.length() > maxChunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current.setLength(0);
                }
                for (int start = 0; start < value.length(); start += maxChunkSize) {
                    int end = Math.min(value.length(), start + maxChunkSize);
                    chunks.add(value.substring(start, end));
                }
            } else {
                if (current.length() > 0) {
                    current.append(System.lineSeparator()).append(System.lineSeparator());
                }
                current.append(value);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }
}
