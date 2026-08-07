package com.example.spring.xhs.analysis;

import com.example.spring.xhs.config.XhsCommentAnalysisProperties;
import com.example.spring.xhs.repository.XhsCommentAnalysisRepository;
import com.example.spring.xhs.schedule.XhsNegativePostEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

class XhsCommentAnalysisPipelineTests {

    private final XhsCommentAnalysisCandidate first = candidate(11L, "过敏红肿");
    private final XhsCommentAnalysisCandidate second = candidate(12L, "不推荐");
    private final XhsCommentAnalysisPipeline pipeline = new XhsCommentAnalysisPipeline(
            mock(XhsCommentAnalysisRepository.class),
            mock(XhsAnalysisLlmClient.class),
            new ObjectMapper(),
            new XhsCommentAnalysisProperties(
                    true, "test-v1", 10, 3, 25, 5, Duration.ofMinutes(15)));

    @Test
    void parsesOneResultForEveryClaimedComment() {
        Map<Long, XhsCommentAssessment> result = pipeline.parse("""
                {"results":[
                  {"analysisId":11,"negative":true,"riskScore":85,"confidence":0.92,
                   "summary":"疑似不良反应","evidence":["过敏红肿"]},
                  {"analysisId":12,"negative":false,"riskScore":5,"confidence":0.8,
                   "summary":"缺少具体问题","evidence":[]}
                ]}
                """, List.of(first, second));

        assertThat(result).containsOnlyKeys(11L, 12L);
        assertThat(result.get(11L).negative()).isTrue();
        assertThat(result.get(11L).riskScore()).isEqualTo(85);
        assertThat(result.get(12L).negative()).isFalse();
    }

    @Test
    void rejectsResponsesThatOmitAClaimedComment() {
        assertThatThrownBy(() -> pipeline.parse(
                "{\"results\":[{\"analysisId\":11,\"negative\":true}]}",
                List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("omitted");
    }

    @Test
    void enqueuesEmailAfterModelConfirmsNegativeComment() {
        XhsCommentAnalysisRepository repository = mock(XhsCommentAnalysisRepository.class);
        XhsAnalysisLlmClient client = mock(XhsAnalysisLlmClient.class);
        XhsNegativePostEmailService emailService = mock(XhsNegativePostEmailService.class);
        when(repository.claimPending(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(first));
        when(client.analyze(anyString())).thenReturn(new XhsAnalysisLlmClient.Response(
                "{\"results\":[{\"analysisId\":11,\"negative\":true,\"riskScore\":85,"
                        + "\"confidence\":0.92,\"summary\":\"疑似不良反应\",\"evidence\":[\"过敏红肿\"]}]}",
                "test-model", 10, 5, 15, 20));
        XhsCommentAnalysisPipeline value = new XhsCommentAnalysisPipeline(
                repository, client, new ObjectMapper(),
                new XhsCommentAnalysisProperties(
                        true, "test-v1", 10, 3, 25, 5, Duration.ofMinutes(15)),
                emailService);

        assertThat(value.processPending()).isEqualTo(1);

        verify(emailService).enqueue(2L);
    }

    private XhsCommentAnalysisCandidate candidate(long id, String content) {
        return new XhsCommentAnalysisCandidate(
                id, 2L, "comment-" + id, content, 10, 50, "claim");
    }
}
