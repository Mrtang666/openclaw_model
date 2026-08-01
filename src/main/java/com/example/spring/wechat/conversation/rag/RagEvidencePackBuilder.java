package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagEvidencePackBuilder {

    private final RagContextFormatter formatter;

    public RagEvidencePackBuilder(RagContextFormatter formatter) {
        this.formatter = formatter;
    }

    public String build(List<KnowledgeSearchResult> results, int maxContextChars, boolean includeSources) {
        return formatter.format(evidence(results), maxContextChars, includeSources);
    }

    List<KnowledgeSearchResult> evidence(List<KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeSearchResult> deduplicated = new LinkedHashMap<>();
        for (KnowledgeSearchResult result : results) {
            if (result == null) {
                continue;
            }
            String key = result.documentId() + ":" + result.chunkIndex();
            KnowledgeSearchResult existing = deduplicated.get(key);
            if (existing == null || result.score() > existing.score()) {
                deduplicated.put(key, result);
            }
        }

        List<KnowledgeSearchResult> values = new ArrayList<>(deduplicated.values());
        List<KnowledgeSearchResult> merged = new ArrayList<>();
        for (KnowledgeSearchResult result : values) {
            if (merged.isEmpty()) {
                merged.add(result);
                continue;
            }
            KnowledgeSearchResult previous = merged.get(merged.size() - 1);
            if (isAdjacent(previous, result)) {
                merged.set(merged.size() - 1, merge(previous, result));
            } else {
                merged.add(result);
            }
        }
        return merged;
    }

    private boolean isAdjacent(KnowledgeSearchResult left, KnowledgeSearchResult right) {
        return left.documentId() == right.documentId()
                && left.chunkIndex() + 1 == right.chunkIndex()
                && safe(left.title()).equals(safe(right.title()))
                && safe(left.sourceUrl()).equals(safe(right.sourceUrl()));
    }

    private KnowledgeSearchResult merge(KnowledgeSearchResult left, KnowledgeSearchResult right) {
        String content = safe(left.content());
        String nextContent = safe(right.content());
        if (!nextContent.isBlank() && !content.contains(nextContent)) {
            content = content.isBlank() ? nextContent : content + System.lineSeparator() + nextContent;
        }
        return new KnowledgeSearchResult(
                left.documentId(),
                left.title(),
                left.chunkIndex(),
                content,
                firstNonBlank(left.sourceType(), right.sourceType()),
                firstNonBlank(left.sourceUrl(), right.sourceUrl()),
                Math.max(left.score(), right.score()));
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? safe(second) : first.strip();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
