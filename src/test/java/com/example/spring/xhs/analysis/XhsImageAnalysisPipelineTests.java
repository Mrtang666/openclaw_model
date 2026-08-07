package com.example.spring.xhs.analysis;

import com.example.spring.xhs.config.XhsImageAnalysisProperties;
import com.example.spring.xhs.repository.XhsImageAnalysisRepository;
import com.example.spring.xhs.schedule.XhsNegativePostEmailService;
import com.example.spring.wechat.image.client.ImageUnderstandingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class XhsImageAnalysisPipelineTests {

    @Test
    void reusesHashCacheWithoutCallingVisionModel() {
        FakeRepository repository = new FakeRepository();
        repository.cached = true;
        ImageUnderstandingClient client = mock(ImageUnderstandingClient.class);
        XhsImageAnalysisPipeline pipeline = pipeline(repository, client);

        assertThat(pipeline.processPending()).isEqualTo(1);
        assertThat(repository.saved).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void reevaluatesNegativeEmailAfterReusingCachedAnalysis() {
        FakeRepository repository = new FakeRepository();
        repository.cached = true;
        XhsNegativePostEmailService emailService = mock(XhsNegativePostEmailService.class);
        XhsImageAnalysisPipeline pipeline = new XhsImageAnalysisPipeline(
                repository, mock(ImageUnderstandingClient.class), new ObjectMapper(),
                new XhsImageAnalysisProperties(true, "test-v1", 5, 3, Duration.ofMinutes(15)),
                emailService);

        pipeline.processPending();

        verify(emailService).enqueue(2L);
    }

    @Test
    void parsesStrictJsonInsideMarkdownFence() {
        XhsImageAnalysisPipeline pipeline = pipeline(
                new FakeRepository(), mock(ImageUnderstandingClient.class));
        String fence = String.valueOf((char) 96).repeat(3);

        XhsImageAssessment result = pipeline.parse(fence + "json\n"
                + "{\"negative\":true,\"riskScore\":82,\"containsProduct\":true,"
                + "\"summary\":\"包装破损\",\"evidence\":[\"瓶身开裂\"]}\n" + fence);

        assertThat(result.negative()).isTrue();
        assertThat(result.riskScore()).isEqualTo(82);
        assertThat(result.containsProduct()).isTrue();
        assertThat(result.evidence()).containsExactly("瓶身开裂");
    }

    private XhsImageAnalysisPipeline pipeline(
            XhsImageAnalysisRepository repository, ImageUnderstandingClient client) {
        return new XhsImageAnalysisPipeline(repository, client, new ObjectMapper(),
                new XhsImageAnalysisProperties(true, "test-v1", 5, 3, Duration.ofMinutes(15)));
    }

    private static final class FakeRepository implements XhsImageAnalysisRepository {
        private final XhsImageAnalysisCandidate candidate = new XhsImageAnalysisCandidate(
                1L, 2L, "hash", "https://sns-img-qc.xhscdn.com/a.jpg",
                "title", "content", "claim");
        private boolean cached;
        private XhsImageAssessment saved;

        @Override
        public List<XhsImageAnalysisCandidate> claimPending(
                String version, int limit, int maxAttempts, Instant staleBefore) {
            return List.of(candidate);
        }

        @Override
        public boolean reuseCached(
                XhsImageAnalysisCandidate value, String version, Instant analyzedAt) {
            return cached;
        }

        @Override
        public void save(XhsImageAnalysisCandidate value, String version,
                         XhsImageAssessment assessment, Instant analyzedAt) {
            saved = assessment;
        }

        @Override
        public void fail(XhsImageAnalysisCandidate value, int maxAttempts,
                         String message, Instant failedAt) {
        }
    }
}
