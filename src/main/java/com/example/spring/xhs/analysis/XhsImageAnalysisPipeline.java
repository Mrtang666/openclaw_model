package com.example.spring.xhs.analysis;

import com.example.spring.xhs.config.XhsImageAnalysisProperties;
import com.example.spring.xhs.repository.XhsImageAnalysisRepository;
import com.example.spring.xhs.schedule.XhsNegativePostEmailService;
import com.example.spring.wechat.image.client.ImageUnderstandingClient;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class XhsImageAnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(XhsImageAnalysisPipeline.class);
    private final XhsImageAnalysisRepository repository;
    private final ImageUnderstandingClient imageClient;
    private final ObjectMapper objectMapper;
    private final XhsImageAnalysisProperties properties;
    private final XhsNegativePostEmailService negativePostEmailService;

    @Autowired
    public XhsImageAnalysisPipeline(
            XhsImageAnalysisRepository repository,
            ImageUnderstandingClient imageClient,
            ObjectMapper objectMapper,
            XhsImageAnalysisProperties properties,
            XhsNegativePostEmailService negativePostEmailService) {
        this.repository = repository;
        this.imageClient = imageClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.negativePostEmailService = negativePostEmailService;
    }

    XhsImageAnalysisPipeline(
            XhsImageAnalysisRepository repository,
            ImageUnderstandingClient imageClient,
            ObjectMapper objectMapper,
            XhsImageAnalysisProperties properties) {
        this(repository, imageClient, objectMapper, properties, null);
    }

    public int processPending() {
        if (!properties.enabled()) {
            return 0;
        }
        List<XhsImageAnalysisCandidate> candidates = repository.claimPending(
                properties.version(), properties.batchSize(), properties.maxAttempts(),
                Instant.now().minus(properties.claimTimeout()));
        for (XhsImageAnalysisCandidate candidate : candidates) {
            long started = System.nanoTime();
            ImageUnderstandingClient.Response response = null;
            try {
                Instant now = Instant.now();
                if (repository.reuseCached(candidate, properties.version(), now)) {
                    recordExecution(candidate, null, "CACHE", "", started, now);
                    enqueueNegativeEmail(candidate.postId());
                    continue;
                }
                String prompt = """
                        Analyze this Xiaohongshu product image for public-opinion risk.
                        Return JSON only with keys:
                        negative (boolean), riskScore (0-100 integer), containsProduct (boolean),
                        summary (short Chinese string), evidence (array of short Chinese strings).
                        Mark negative only for visible product defects, adverse reactions, safety warnings,
                        complaints, misleading claims, or clearly negative feedback shown in the image.
                        Post title: %s
                        Post text: %s
                        """.formatted(safe(candidate.title()), safe(candidate.content()));
                response = imageClient.replyWithUsage(new ImageAnalysisRequest(
                        prompt, List.of(new WechatIncomingImage(
                                ImageSourceType.TEXT_URL, candidate.imageUrl()))));
                XhsImageAssessment assessment = parse(response.content());
                repository.save(candidate, properties.version(), assessment, now);
                recordExecution(candidate, response, "SUCCEEDED", "", started, now);
                if (assessment.negative()) {
                    enqueueNegativeEmail(candidate.postId());
                }
            } catch (RuntimeException exception) {
                repository.fail(candidate, properties.maxAttempts(), exception.getMessage(), Instant.now());
                recordExecution(candidate, response, "FAILED", exception.getMessage(), started, Instant.now());
                log.warn("小红书图片分析失败，已记录重试状态 imageId={} error={}",
                        candidate.imageId(), exception.getMessage());
            }
        }
        return candidates.size();
    }

    private void recordExecution(
            XhsImageAnalysisCandidate candidate, ImageUnderstandingClient.Response response,
            String status, String error, long started, Instant now) {
        try {
            long duration = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
            repository.recordExecution(candidate, properties.version(), response, status, error, duration, now);
        } catch (RuntimeException ignored) {
            // Telemetry failure must not alter image analysis completion or retry state.
        }
    }

    private void enqueueNegativeEmail(long postId) {
        if (negativePostEmailService == null) {
            return;
        }
        try {
            negativePostEmailService.enqueue(postId);
        } catch (RuntimeException exception) {
            log.warn("小红书图片风险邮件入队失败，不影响分析结果 postId={} error={}",
                    postId, exception.getMessage());
        }
    }

    XhsImageAssessment parse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            List<String> evidence = new ArrayList<>();
            JsonNode evidenceNode = node.path("evidence");
            if (evidenceNode.isArray()) {
                evidenceNode.forEach(item -> {
                    if (item.isTextual()) {
                        evidence.add(item.asText());
                    }
                });
            } else if (evidenceNode.isTextual()) {
                evidence.add(evidenceNode.asText());
            }
            boolean negative = node.path("negative").asBoolean(false);
            return new XhsImageAssessment(negative,
                    node.path("riskScore").asInt(negative ? 60 : 0),
                    node.path("containsProduct").asBoolean(false),
                    node.path("summary").asText(""), evidence);
        } catch (Exception exception) {
            throw new IllegalArgumentException("视觉模型未返回有效 JSON", exception);
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("视觉模型返回为空");
        }
        String fence = String.valueOf((char) 96).repeat(3);
        String value = raw.strip().replace(fence + "json", "").replace(fence, "").strip();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("视觉模型返回缺少 JSON 对象");
        }
        return value.substring(start, end + 1);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "无";
        }
        String compact = value.replaceAll("\\s+", " ").strip();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
