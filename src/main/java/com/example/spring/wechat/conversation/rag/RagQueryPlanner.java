package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.service.KnowledgeQueryPlanner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RagQueryPlanner {

    private final KnowledgeQueryPlanner delegate;

    public RagQueryPlanner(KnowledgeQueryPlanner delegate) {
        this.delegate = delegate;
    }

    public List<String> plan(String question) {
        String text = normalize(question);
        if (text.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(text);
        if (delegate != null) {
            try {
                queries.addAll(delegate.planQueries(text));
            } catch (RuntimeException ignored) {
                // Query planning is optional; rule-based variants keep retrieval available.
            }
        }
        queries.add(compact(text));
        queries.add(topicFocused(text));

        return queries.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private String compact(String text) {
        return text.replace("根据知识库", " ")
                .replace("根据资料", " ")
                .replace("帮我", " ")
                .replace("请", " ")
                .replace("讲讲", " ")
                .replace("说说", " ")
                .replace("这个", " ")
                .replace("是什么", " ")
                .replace("？", " ")
                .replace("?", " ");
    }

    private String topicFocused(String text) {
        String value = compact(text)
                .replace("的", " ")
                .replace("一下", " ")
                .replace("怎么", " ")
                .replace("如何", " ")
                .replace("为什么", " ");
        if (text.contains("流程") && !value.contains("流程")) {
            value = value + " 流程";
        }
        if (text.contains("架构") && !value.contains("架构")) {
            value = value + " 架构";
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }
}
