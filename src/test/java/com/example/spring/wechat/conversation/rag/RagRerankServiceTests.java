package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRerankServiceTests {

    private final RagRerankService rerankService = new RagRerankService();

    @Test
    void reranksByVectorScoreAndLexicalOverlap() {
        KnowledgeSearchResult unrelatedHighVectorScore = result(
                1,
                "天气工具说明",
                "天气查询参数和城市解析",
                0.95);
        KnowledgeSearchResult titleAndContentMatch = result(
                2,
                "Function Calling 流程设计",
                "WechatConversationService 构造请求后进入 Function Calling Agent Loop。",
                0.72);

        List<KnowledgeSearchResult> ranked = rerankService.rank(
                "这个项目的 Function Calling 流程是什么",
                List.of(unrelatedHighVectorScore, titleAndContentMatch));

        assertThat(ranked.get(0).documentId()).isEqualTo(2);
    }

    @Test
    void keepsStableOrderForEqualScores() {
        KnowledgeSearchResult first = result(1, "项目说明", "流程", 0.8);
        KnowledgeSearchResult second = result(2, "项目说明", "流程", 0.8);

        List<KnowledgeSearchResult> ranked = rerankService.rank("项目流程", List.of(first, second));

        assertThat(ranked).containsExactly(first, second);
    }

    private KnowledgeSearchResult result(long documentId, String title, String content, double score) {
        return new KnowledgeSearchResult(documentId, title, 0, content, "text", "", score);
    }
}
