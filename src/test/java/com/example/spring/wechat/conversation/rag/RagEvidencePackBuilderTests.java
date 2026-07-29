package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvidencePackBuilderTests {

    private final RagEvidencePackBuilder builder = new RagEvidencePackBuilder(new RagContextFormatter());

    @Test
    void buildsDeduplicatedEvidencePack() {
        KnowledgeSearchResult first = result(1, 0, "重复两次的同一段", 0.9);
        KnowledgeSearchResult duplicate = result(1, 0, "重复两次的同一段", 0.8);

        String pack = builder.build(List.of(first, duplicate), 2000, true);

        assertThat(pack).contains("[知识1]");
        assertThat(pack).doesNotContain("[知识2]");
        assertThat(pack).containsOnlyOnce("重复两次的同一段");
    }

    @Test
    void mergesAdjacentChunksFromSameDocument() {
        KnowledgeSearchResult first = result(2, 0, "第一段说明检索流程。", 0.9);
        KnowledgeSearchResult second = result(2, 1, "第二段说明生成流程。", 0.88);

        String pack = builder.build(List.of(first, second), 2000, true);

        assertThat(pack).contains("[知识1]");
        assertThat(pack).doesNotContain("[知识2]");
        assertThat(pack).contains("第一段说明检索流程");
        assertThat(pack).contains("第二段说明生成流程");
    }

    @Test
    void respectsContextLimit() {
        KnowledgeSearchResult result = result(3, 0, "很长的资料内容".repeat(80), 0.9);

        String pack = builder.build(List.of(result), 260, true);

        assertThat(pack.length()).isLessThanOrEqualTo(320);
        assertThat(pack).contains("...");
    }

    private KnowledgeSearchResult result(long documentId, int chunkIndex, String content, double score) {
        return new KnowledgeSearchResult(
                documentId,
                "Function Calling 设计",
                chunkIndex,
                content,
                "text",
                "https://example.com/doc-" + documentId,
                score);
    }
}
