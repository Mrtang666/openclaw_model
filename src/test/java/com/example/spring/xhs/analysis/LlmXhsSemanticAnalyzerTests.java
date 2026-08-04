package com.example.spring.xhs.analysis;

import com.example.spring.chat.ChatService;
import com.example.spring.xhs.config.XhsAnalysisProperties;
import com.example.spring.xhs.model.XhsMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    void reusesSemanticCacheWithoutCallingModel() {
        XhsAnalysisLlmClient llmClient = mock(XhsAnalysisLlmClient.class);
        XhsSemanticCache cache = mock(XhsSemanticCache.class);
        XhsAnalysisTelemetry telemetry = mock(XhsAnalysisTelemetry.class);
        XhsAnalysisCandidate candidate = candidate("相同的帖子内容");
        XhsSemanticAssessment cached = new XhsSemanticAssessment(
                XhsSentiment.NEUTRAL, 0, java.util.List.of("GENERAL"), "GENERAL",
                1, 0.9, "缓存结果", java.util.List.of());
        when(cache.find(candidate, "v2")).thenReturn(new XhsSemanticCache.Cached(cached, "qwen-plus"));

        XhsSemanticAssessment result = new LlmXhsSemanticAnalyzer(
                llmClient, new ObjectMapper(), telemetry, cache,
                new XhsAnalysisProperties(true, "v2", 20, 60, 0.65)).analyze(candidate);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(llmClient);
        verify(telemetry).cache(candidate.postId(), "v2", "qwen-plus");
    }

    private XhsAnalysisCandidate candidate(String content) {
        return new XhsAnalysisCandidate(1, 2, "brand-a", "体验", content,
                "https://www.xiaohongshu.com/explore/1", Instant.now(), Instant.now(), XhsMetrics.empty());
    }
}
