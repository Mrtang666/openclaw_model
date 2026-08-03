package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class MemoryGraphMaintenanceService {

    private final WechatContextProperties properties;
    private final MemoryGraphRepository repository;
    private final KnowledgeIngestionService ingestionService;
    private final LongTermMemoryExtractor longTermMemoryExtractor;
    private final SlidingWindowSummaryService slidingWindowSummaryService;
    private final ActiveExtractService activeExtractService;

    @Autowired
    public MemoryGraphMaintenanceService(
            WechatContextProperties properties,
            MemoryGraphRepository repository,
            KnowledgeIngestionService ingestionService,
            LongTermMemoryExtractor longTermMemoryExtractor,
            SlidingWindowSummaryService slidingWindowSummaryService,
            ActiveExtractService activeExtractService) {
        this.properties = properties;
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
        this.slidingWindowSummaryService = slidingWindowSummaryService;
        this.activeExtractService = activeExtractService;
    }

    MemoryGraphMaintenanceService(
            WechatContextProperties properties,
            MemoryGraphRepository repository,
            KnowledgeIngestionService ingestionService,
            LongTermMemoryExtractor longTermMemoryExtractor) {
        this(properties, repository, ingestionService, longTermMemoryExtractor, null, null);
    }

    public void ingestLongTermMemories(String sessionKey, Long conversationId, String transcript) {
        if (properties == null || !properties.longTermMemoryIngestionEnabled()
                || repository == null || ingestionService == null || longTermMemoryExtractor == null) {
            return;
        }
        List<MemoryGraphNodeDraft> drafts = longTermMemoryExtractor.extract(sessionKey, conversationId, transcript);
        for (MemoryGraphNodeDraft draft : drafts == null ? List.<MemoryGraphNodeDraft>of() : drafts) {
            MemoryGraphNode node = repository.createNode(draft);
            ingestionService.add(
                    sessionKey,
                    draft.title(),
                    draft.content(),
                    "memory_graph",
                    "memory://long_term_memory/" + node.id(),
                    "memory_type:long_term_memory");
        }
    }

    public void maintainConversationWindow(
            String sessionKey,
            Long conversationId,
            WechatConversationMemory memory,
            String currentTopic) {
        if (properties == null || !properties.memoryGraphEnabled()
                || repository == null || slidingWindowSummaryService == null || memory == null) {
            return;
        }
        int recentTurns = Math.max(properties.minRecentTurns(), properties.strongRecentTurns());
        String existingSummary = memory.conversationSummary().orElse("");
        String summary = clean(existingSummary);
        if (summary.isBlank() && memory.snapshot().size() > recentTurns) {
            summary = clean(slidingWindowSummaryService.summarizeOutsideRecentWindow(memory, recentTurns));
        }
        if (summary.isBlank()) {
            return;
        }

        memory.conversationSummary(summary);
        MemoryGraphNode summaryNode = createNode(new MemoryGraphNodeDraft(
                sessionKey,
                conversationId,
                MemoryNodeType.CONVERSATION_SUMMARY,
                topicKey(currentTopic),
                "滑动窗口摘要",
                summary,
                summary,
                0.7,
                0.6,
                0.85,
                null,
                null,
                "conversation",
                sourceRef(conversationId),
                "memory_type:conversation_summary",
                null));
        addToRag(sessionKey, summaryNode, "memory_type:conversation_summary");

        String topicTitle = topicTitle(currentTopic);
        MemoryGraphNode topicNode = createNode(new MemoryGraphNodeDraft(
                sessionKey,
                conversationId,
                MemoryNodeType.CONVERSATION_TOPIC,
                topicKey(currentTopic),
                topicTitle,
                summary,
                summary,
                0.65,
                0.5,
                0.8,
                null,
                null,
                "conversation",
                sourceRef(conversationId),
                "memory_type:conversation_topic",
                null));
        addToRag(sessionKey, topicNode, "memory_type:conversation_topic");

        if (activeExtractService == null || clean(currentTopic).isBlank()) {
            return;
        }
        String activeExtract = clean(activeExtractService.extract(currentTopic, summary));
        if (activeExtract.isBlank()) {
            return;
        }
        MemoryGraphNode activeNode = createNode(new MemoryGraphNodeDraft(
                sessionKey,
                conversationId,
                MemoryNodeType.ACTIVE_EXTRACT,
                topicKey(currentTopic),
                "活摘：" + clean(currentTopic),
                activeExtract,
                activeExtract,
                0.8,
                0.9,
                0.85,
                null,
                null,
                "conversation",
                sourceRef(conversationId),
                "memory_type:active_extract",
                null));
        addToRag(sessionKey, activeNode, "memory_type:active_extract");
    }

    private MemoryGraphNode createNode(MemoryGraphNodeDraft draft) {
        return repository.createNode(draft);
    }

    private void addToRag(String sessionKey, MemoryGraphNode node, String tag) {
        if (ingestionService == null || node == null || node.content() == null || node.content().isBlank()) {
            return;
        }
        ingestionService.add(
                sessionKey,
                node.title(),
                node.content(),
                "memory_graph",
                "memory://" + tag.replace("memory_type:", "") + "/" + node.id(),
                tag);
    }

    private String topicTitle(String currentTopic) {
        String topic = clean(currentTopic);
        return topic.isBlank() ? "历史主题" : "历史主题：" + topic;
    }

    private String topicKey(String value) {
        String text = clean(value);
        if (text.isBlank()) {
            return "conversation-summary";
        }
        return text.replaceAll("\\s+", "-").toLowerCase(Locale.ROOT);
    }

    private String sourceRef(Long conversationId) {
        return conversationId == null ? "" : "conversation://" + conversationId;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
