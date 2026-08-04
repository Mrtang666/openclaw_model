package com.example.spring.xhs.ingestion;

import com.example.spring.xhs.config.XhsCollectorProperties;
import com.example.spring.xhs.model.XhsCollectionJob;
import com.example.spring.xhs.model.XhsImportResult;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.repository.XhsCollectionJobRepository;
import com.example.spring.xhs.repository.XhsCollectionClaim;
import com.example.spring.xhs.repository.XhsOpinionRepository;
import com.example.spring.xhs.source.XhsCollectionRequest;
import com.example.spring.xhs.source.XhsCollectionStatus;
import com.example.spring.xhs.source.XhsCollectorJobResult;
import com.example.spring.xhs.source.XhsCollectorSubmission;
import com.example.spring.xhs.source.XhsSourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XhsCollectionCoordinatorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitsAndImportsCompletedCollectorJob() throws Exception {
        FakeSourceClient source = new FakeSourceClient();
        FakeJobRepository jobs = new FakeJobRepository();
        XhsOpinionRepository opinions = mock(XhsOpinionRepository.class);
        when(opinions.ensureProject(anyString(), anyString(), any())).thenReturn(9L);
        XhsJsonImportService importer = mock(XhsJsonImportService.class);
        when(importer.importJson(anyString(), anyString(), any())).thenReturn(
                new XhsImportResult("project-a", XhsSourceType.SPIDER_XHS_LAB, 1, 0, 0));
        XhsCollectionCoordinator coordinator = coordinator(source, opinions, jobs, importer, 5);

        String localJobId = coordinator.start(new XhsCollectionRequest(
                "project-a", "项目 A", "品牌 A", 20, ""));
        jobs.pending = List.of(job(localJobId, 0));
        source.result = new XhsCollectorJobResult(
                XhsCollectionStatus.SUCCEEDED,
                true,
                "",
                objectMapper.readTree("[{\"sourcePostId\":\"note-1\"}]"),
                "",
                "",
                Instant.parse("2026-07-28T02:00:00Z"));

        int processed = coordinator.pollPending();

        assertThat(processed).isEqualTo(1);
        assertThat(jobs.externalJobId).isEqualTo("external-1");
        assertThat(jobs.finishedStatus).isEqualTo(XhsCollectionStatus.SUCCEEDED);
        assertThat(jobs.complete).isTrue();
        assertThat(jobs.recordCount).isEqualTo(1);
        assertThat(jobs.releasedClaims).isEqualTo(1);
    }

    @Test
    void marksIncompleteSuccessAsPartial() {
        FakeSourceClient source = new FakeSourceClient();
        FakeJobRepository jobs = new FakeJobRepository();
        jobs.pending = List.of(job("local-1", 1));
        XhsJsonImportService importer = mock(XhsJsonImportService.class);
        when(importer.importJson(anyString(), anyString(), any())).thenReturn(
                new XhsImportResult("project-a", XhsSourceType.SPIDER_XHS_LAB, 3, 0, 0));
        source.result = new XhsCollectorJobResult(
                XhsCollectionStatus.SUCCEEDED, false, "cursor-2", objectMapper.createArrayNode(),
                "PARTIAL_COLLECTION", "two details unavailable", Instant.now());

        coordinator(source, mock(XhsOpinionRepository.class), jobs, importer, 5).pollPending();

        assertThat(jobs.finishedStatus).isEqualTo(XhsCollectionStatus.PARTIAL);
        assertThat(jobs.complete).isFalse();
        assertThat(jobs.nextCursor).isEqualTo("cursor-2");
        assertThat(jobs.errorCode).isEqualTo("PARTIAL_COLLECTION");
        assertThat(jobs.errorMessage).isEqualTo("two details unavailable");
    }

    @Test
    void failsJobAfterPollingLimitWithoutCallingCollector() {
        FakeSourceClient source = new FakeSourceClient();
        FakeJobRepository jobs = new FakeJobRepository();
        jobs.pending = List.of(job("local-1", 3));

        coordinator(source, mock(XhsOpinionRepository.class), jobs, mock(XhsJsonImportService.class), 3).pollPending();

        assertThat(source.getCalls).isZero();
        assertThat(jobs.finishedStatus).isEqualTo(XhsCollectionStatus.FAILED);
        assertThat(jobs.errorCode).isEqualTo("POLL_LIMIT");
    }

    private XhsCollectionCoordinator coordinator(
            XhsSourceClient source,
            XhsOpinionRepository opinions,
            XhsCollectionJobRepository jobs,
            XhsJsonImportService importer,
            int maxAttempts) {
        return new XhsCollectionCoordinator(
                source,
                opinions,
                jobs,
                importer,
                new XhsCollectorProperties(true, "http://collector.test", "", Duration.ofSeconds(1), Duration.ofSeconds(1), maxAttempts),
                objectMapper);
    }

    private XhsCollectionJob job(String jobKey, int attempts) {
        return new XhsCollectionJob(
                jobKey, 9L, "project-a", "项目 A", XhsSourceType.SPIDER_XHS_LAB,
                "品牌 A", "external-1", XhsCollectionStatus.SUBMITTED, attempts, Instant.now());
    }

    private static final class FakeSourceClient implements XhsSourceClient {
        private XhsCollectorJobResult result;
        private int getCalls;

        @Override
        public XhsCollectorSubmission submitSearch(XhsCollectionRequest request) {
            return new XhsCollectorSubmission("external-1");
        }

        @Override
        public XhsCollectorJobResult getJob(String externalJobId) {
            getCalls++;
            return result;
        }
    }

    private static final class FakeJobRepository implements XhsCollectionJobRepository {
        private List<XhsCollectionJob> pending = new ArrayList<>();
        private String externalJobId;
        private XhsCollectionStatus finishedStatus;
        private boolean complete;
        private int recordCount;
        private String nextCursor;
        private String errorCode;
        private String errorMessage;
        private int releasedClaims;

        @Override
        public void create(String jobKey, long projectId, XhsSourceType sourceType, String query, Instant now) {
        }

        @Override
        public void markSubmitted(String jobKey, String externalJobId) {
            this.externalJobId = externalJobId;
        }

        @Override
        public List<XhsCollectionJob> findPending(int limit) {
            return pending;
        }

        @Override
        public void recordPoll(String jobKey, XhsCollectionStatus status) {
        }

        @Override
        public void releaseClaim(XhsCollectionClaim claim) {
            releasedClaims++;
        }

        @Override
        public void finish(String jobKey, XhsCollectionStatus status, boolean complete, int recordCount,
                           String nextCursor, String errorCode, String errorMessage, Instant finishedAt) {
            this.finishedStatus = status;
            this.complete = complete;
            this.recordCount = recordCount;
            this.nextCursor = nextCursor;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }
}
