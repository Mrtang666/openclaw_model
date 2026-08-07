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
import com.example.spring.xhs.source.XhsSourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class XhsCollectionCoordinator {

    private final XhsSourceClient sourceClient;
    private final XhsOpinionRepository opinionRepository;
    private final XhsCollectionJobRepository jobRepository;
    private final XhsJsonImportService importService;
    private final XhsCollectorProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public XhsCollectionCoordinator(
            ObjectProvider<XhsSourceClient> sourceClientProvider,
            XhsOpinionRepository opinionRepository,
            XhsCollectionJobRepository jobRepository,
            XhsJsonImportService importService,
            XhsCollectorProperties properties,
            ObjectMapper objectMapper) {
        this(sourceClientProvider.getIfAvailable(), opinionRepository, jobRepository, importService, properties, objectMapper);
    }

    XhsCollectionCoordinator(
            XhsSourceClient sourceClient,
            XhsOpinionRepository opinionRepository,
            XhsCollectionJobRepository jobRepository,
            XhsJsonImportService importService,
            XhsCollectorProperties properties,
            ObjectMapper objectMapper) {
        this.sourceClient = sourceClient;
        this.opinionRepository = opinionRepository;
        this.jobRepository = jobRepository;
        this.importService = importService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String start(XhsCollectionRequest request) {
        if (sourceClient == null || !properties.enabled()) {
            throw new IllegalStateException("小红书采集侧车未启用");
        }
        Instant now = Instant.now();
        long projectId = opinionRepository.ensureProject(request.projectKey(), request.projectName(), now);
        String jobKey = UUID.randomUUID().toString();
        jobRepository.create(jobKey, projectId, XhsSourceType.SPIDER_XHS_LAB, request.query(),
                request.limit(), request.cursor(), request.sortMode(),
                request.timeRange(), request.noteType(), request.commentLimit(), now);
        try {
            String externalJobId = sourceClient.submitSearch(request).externalJobId();
            jobRepository.markSubmitted(jobKey, externalJobId);
            return jobKey;
        } catch (RuntimeException exception) {
            jobRepository.finish(jobKey, XhsCollectionStatus.FAILED, false, 0, "",
                    "SUBMIT_FAILED", safeMessage(exception), Instant.now());
            throw exception;
        }
    }

    public int pollPending() {
        if (sourceClient == null || !properties.enabled()) {
            return 0;
        }
        List<XhsCollectionClaim> claims = jobRepository.claimPending(20);
        claims.forEach(this::poll);
        return claims.size();
    }

    private void poll(XhsCollectionClaim claim) {
        XhsCollectionJob job = claim.job();
        try {
            if (job.attemptCount() >= properties.maxAttempts()) {
                jobRepository.finish(claim, XhsCollectionStatus.FAILED, false, 0, "",
                        "POLL_LIMIT", "采集侧车轮询次数达到上限", Instant.now());
                return;
            }
            XhsCollectorJobResult result = sourceClient.getJob(job.externalJobId());
            if (!result.status().terminal()) {
                jobRepository.recordPoll(claim, XhsCollectionStatus.RUNNING, "",
                        Instant.now().plusSeconds(10));
                return;
            }
            if (result.status() == XhsCollectionStatus.FAILED) {
                jobRepository.finish(claim, XhsCollectionStatus.FAILED, false, 0,
                        result.nextCursor(), result.errorCode(), result.errorMessage(), Instant.now());
                return;
            }
            XhsImportResult imported = importService.importJson(
                    job.projectKey(),
                    job.projectName(),
                    job.jobKey(),
                    job.query(),
                    new ByteArrayInputStream(importPayload(result).getBytes(StandardCharsets.UTF_8)));
            jobRepository.recordImportStats(job.jobKey(), imported);
            XhsCollectionStatus finalStatus = result.complete()
                    ? XhsCollectionStatus.SUCCEEDED
                    : XhsCollectionStatus.PARTIAL;
            jobRepository.finish(claim, finalStatus, result.complete(), imported.postCount(),
                    result.nextCursor(), result.errorCode(), result.errorMessage(), Instant.now());
        } catch (RuntimeException exception) {
            long delaySeconds = Math.min(900L, Math.max(10L, 5L * (1L << Math.min(job.attemptCount(), 7))));
            jobRepository.recordPoll(claim, XhsCollectionStatus.RUNNING, safeMessage(exception),
                    Instant.now().plusSeconds(delaySeconds));
        } finally {
            jobRepository.releaseClaim(claim);
        }
    }

    private String importPayload(XhsCollectorJobResult result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("source", XhsSourceType.SPIDER_XHS_LAB.name());
        root.put("collectedAt", result.collectedAt().toString());
        root.set("posts", result.records() == null ? objectMapper.createArrayNode() : result.records());
        return root.toString();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
