package com.example.spring.document;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextChunker {
    private final DocumentProperties properties;

    public DocumentTextChunker(DocumentProperties properties) {
        this.properties = properties;
    }

    public List<String> chunk(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(2_000, properties.getChunkCharacters());
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + limit);
            if (end < value.length()) {
                int paragraph = value.lastIndexOf("\n\n", end);
                int sentence = Math.max(value.lastIndexOf('。', end), value.lastIndexOf('.', end));
                int boundary = Math.max(paragraph, sentence);
                if (boundary > start + limit / 2) {
                    end = boundary + 1;
                }
            }
            chunks.add(value.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }
}
