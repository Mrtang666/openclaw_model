package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagContextFormatterTests {

    private final RagContextFormatter formatter = new RagContextFormatter();

    @Test
    void formatsKnowledgeResultsForPrompt() {
        String context = formatter.format(List.of(result(
                1,
                "项目说明",
                2,
                "Function Calling Agent Loop 会执行工具并把结果回传给模型。",
                "https://example.com/openclaw",
                0.91)), 2000, true);

        assertThat(context)
                .contains("knowledge_context")
                .contains("知识库片段是事实资料")
                .contains("[知识1]")
                .contains("标题：项目说明")
                .contains("document_id=1")
                .contains("chunk_index=2")
                .contains("匹配分数：0.910")
                .contains("来源：https://example.com/openclaw")
                .contains("Function Calling Agent Loop");
    }

    @Test
    void omitsSourcesWhenDisabled() {
        String context = formatter.format(List.of(result(
                2,
                "无来源",
                0,
                "知识内容",
                "https://example.com/hidden",
                0.8)), 2000, false);

        assertThat(context).doesNotContain("来源：https://example.com/hidden");
    }

    @Test
    void returnsBlankForNoResults() {
        assertThat(formatter.format(List.of(), 2000, true)).isBlank();
        assertThat(formatter.format(null, 2000, true)).isBlank();
    }

    @Test
    void truncatesLongContentButKeepsMetadata() {
        String longContent = "这是一段很长的资料内容，用来验证截断逻辑仍然保留元数据。".repeat(20);

        String context = formatter.format(List.of(result(
                3,
                "长文档",
                4,
                longContent,
                "",
                0.77)), 260, true);

        assertThat(context)
                .contains("[知识1]")
                .contains("标题：长文档")
                .contains("document_id=3")
                .contains("内容：")
                .contains("...");
        assertThat(context.length()).isLessThanOrEqualTo(320);
    }

    private KnowledgeSearchResult result(
            long documentId,
            String title,
            int chunkIndex,
            String content,
            String sourceUrl,
            double score) {
        return new KnowledgeSearchResult(documentId, title, chunkIndex, content, "text", sourceUrl, score);
    }
}
