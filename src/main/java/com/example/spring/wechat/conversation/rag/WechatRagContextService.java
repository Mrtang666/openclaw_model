package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WechatRagContextService {

    private static final Logger log = LoggerFactory.getLogger(WechatRagContextService.class);

    private final KnowledgeSearchService searchService;
    private final RagContextFormatter formatter;
    private final RagProperties properties;

    public WechatRagContextService(
            KnowledgeSearchService searchService,
            RagContextFormatter formatter,
            RagProperties properties) {
        this.searchService = searchService;
        this.formatter = formatter;
        this.properties = properties;
    }

    public String build(String sessionKey, String userText) {
        String query = userText == null ? "" : userText.strip();
        if (!shouldRetrieve(query)) {
            log.debug("RAG 自动检索跳过，userId={}, reason=not_applicable, text={}", sessionKey, preview(query));
            return "";
        }

        long started = System.nanoTime();
        try {
            List<KnowledgeSearchResult> results = searchService.search(
                    sessionKey,
                    query,
                    properties.topK(),
                    "");
            List<KnowledgeSearchResult> filtered = results.stream()
                    .filter(result -> result != null && result.score() >= properties.minScore())
                    .toList();
            String context = formatter.format(filtered, properties.maxContextChars(), properties.includeSources());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            log.info("RAG 自动检索完成，userId={}, hitCount={}, selectedCount={}, topScore={}, elapsedMs={}",
                    sessionKey,
                    results.size(),
                    filtered.size(),
                    topScore(filtered),
                    elapsedMs);
            return context;
        } catch (RuntimeException exception) {
            log.warn("RAG 自动检索失败，userId={}, error={}", sessionKey, rootMessage(exception));
            return "";
        }
    }

    private boolean shouldRetrieve(String query) {
        if (properties == null || !properties.enabled() || !properties.autoRetrieve()) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.strip();
        if ("#new".equals(normalized)) {
            return false;
        }
        if (isShortAcknowledgement(normalized) || isObviousToolIntent(normalized)) {
            return false;
        }
        return true;
    }

    private boolean isShortAcknowledgement(String text) {
        return text.length() <= 3 && List.of("好", "嗯", "可以", "继续", "行", "OK", "ok").contains(text);
    }

    private boolean isObviousToolIntent(String text) {
        return isWeatherRequest(text)
                || containsAny(text,
                "生成一张",
                "画一张",
                "帮我画",
                "发邮件",
                "发送邮件",
                "打车",
                "叫车",
                "语音朗读",
                "用语音",
                "读出来");
    }

    private boolean isWeatherRequest(String text) {
        return text.contains("天气")
                && containsAny(text, "查", "查询", "看看", "今天", "明天", "后天", "气温", "下雨");
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String topScore(List<KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "none";
        }
        return String.format(java.util.Locale.ROOT, "%.3f", results.get(0).score());
    }

    private String preview(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
