package com.example.spring.xhs.analysis;

import com.example.spring.chat.ChatService;
import com.example.spring.xhs.model.XhsMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmXhsSemanticAnalyzerTests {

    @Test
    void parsesStructuredAssessment() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("使用后脸部发红"))).thenReturn("""
                ```json
                {"sentiment":"NEGATIVE","sentimentScore":-0.91,"aspects":["产品安全"],
                 "riskCategory":"CONSUMER_SAFETY","severity":5,"confidence":0.93,
                 "summary":"用户反馈使用后脸部发红","evidence":["使用后脸部发红"]}
                ```
                """);

        XhsSemanticAssessment result = new LlmXhsSemanticAnalyzer(chatService, new ObjectMapper())
                .analyze(candidate("使用后脸部发红"));

        assertThat(result.sentiment()).isEqualTo(XhsSentiment.NEGATIVE);
        assertThat(result.riskCategory()).isEqualTo("CONSUMER_SAFETY");
        assertThat(result.severity()).isEqualTo(5);
        assertThat(result.evidence()).containsExactly("使用后脸部发红");
    }

    @Test
    void fallsBackToRulesWhenModelResponseIsInvalid() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("过敏红肿"))).thenReturn("not-json");

        XhsSemanticAssessment result = new LlmXhsSemanticAnalyzer(chatService, new ObjectMapper())
                .analyze(candidate("使用后过敏红肿"));

        assertThat(result.riskCategory()).isEqualTo("CONSUMER_SAFETY");
        assertThat(result.confidence()).isEqualTo(0.55);
    }

    private XhsAnalysisCandidate candidate(String content) {
        return new XhsAnalysisCandidate(1, 2, "brand-a", "体验", content,
                "https://www.xiaohongshu.com/explore/1", Instant.now(), Instant.now(), XhsMetrics.empty());
    }
}
