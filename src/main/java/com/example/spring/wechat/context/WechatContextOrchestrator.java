package com.example.spring.wechat.context;

import com.example.spring.wechat.memory.model.ConversationTurn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WechatContextOrchestrator {

    private final WechatContextProperties properties;
    private final ConversationRelevanceClassifier relevanceClassifier;
    private final MemoryGraphRetriever graphRetriever;
    private final LongTermMemoryRetriever longTermMemoryRetriever;
    private final ContextBudgetManager budgetManager;
    private final ContextCompressor compressor;
    private final WechatContextAssembler assembler;

    public WechatContextOrchestrator(
            WechatContextProperties properties,
            ConversationRelevanceClassifier relevanceClassifier,
            MemoryGraphRetriever graphRetriever,
            LongTermMemoryRetriever longTermMemoryRetriever,
            ContextBudgetManager budgetManager,
            ContextCompressor compressor,
            WechatContextAssembler assembler) {
        this.properties = properties;
        this.relevanceClassifier = relevanceClassifier;
        this.graphRetriever = graphRetriever;
        this.longTermMemoryRetriever = longTermMemoryRetriever;
        this.budgetManager = budgetManager;
        this.compressor = compressor;
        this.assembler = assembler;
    }

    public WechatContextPackage build(ContextBuildRequest request) {
        if (request == null || request.memory() == null || properties == null || !properties.memoryGraphEnabled()) {
            return new WechatContextPackage(RelevanceLevel.WEAK, "", List.of(), null);
        }
        List<String> recentClassifierTurns = formatTurns(request.memory().recentTurns(properties.strongRecentTurns()));
        List<String> recentTopics = recentTopicContents(request.sessionKey());
        ConversationRelevanceDecision decision = relevanceClassifier == null
                ? ConversationRelevanceDecision.weak("相关性分类器不可用")
                : relevanceClassifier.classify(request.userText(), recentClassifierTurns, recentTopics);

        List<ContextSection> sections = decision.relevance() == RelevanceLevel.STRONG
                ? strongSections(request, decision)
                : weakSections(request, recentTopics);
        int budget = budgetManager.contextBudgetTokens();
        String beforeCompression = renderSections(sections);
        List<ContextSection> compressed = compressor.compress(sections, budget);
        String afterCompression = renderSections(compressed);
        ContextBudgetReport report = budgetManager.report(afterCompression, !beforeCompression.equals(afterCompression));
        String finalText = assembler.assemble(
                decision.relevance(),
                policyText(decision.relevance()),
                compressed,
                report);
        return new WechatContextPackage(decision.relevance(), finalText, selectedNodes(request, decision), report);
    }

    private List<ContextSection> strongSections(ContextBuildRequest request, ConversationRelevanceDecision decision) {
        List<ContextSection> sections = new ArrayList<>();
        sections.add(new ContextSection("recent_turns", "recent_turns / 最近完整对话",
                String.join(System.lineSeparator(), formatTurns(request.memory().recentTurns(properties.strongRecentTurns()))),
                10,
                true));
        sections.add(new ContextSection("active_extract", "active_extract / 当前主题活摘",
                nodeContents(activeExtractNodes(request, decision)),
                60,
                true));
        sections.add(new ContextSection("long_term_memory", "long_term_memory / 长期记忆",
                String.join(System.lineSeparator(), longTermMemories(request)),
                80,
                true));
        sections.add(new ContextSection("resource_context", "resource_context / 可用资源",
                request.resourceContext(),
                70,
                true));
        sections.add(new ContextSection("rag_context", "rag_context / 检索上下文",
                request.ragContext(),
                75,
                true));
        sections.add(new ContextSection("conversation_summary", "conversation_summary / 会话摘要",
                request.memory().conversationSummary().orElse(""),
                50,
                true));
        return sections;
    }

    private List<ContextSection> weakSections(ContextBuildRequest request, List<String> recentTopics) {
        List<ContextSection> sections = new ArrayList<>();
        sections.add(new ContextSection("recent_turns", "recent_turns / 最近完整对话",
                String.join(System.lineSeparator(), formatTurns(request.memory().recentTurns(properties.weakRecentTurns()))),
                10,
                true));
        sections.add(new ContextSection("conversation_topics", "conversation_topics / 历史主题",
                String.join(System.lineSeparator(), recentTopics),
                80,
                true));
        sections.add(new ContextSection("long_term_memory", "long_term_memory / 长期记忆",
                String.join(System.lineSeparator(), longTermMemories(request)),
                70,
                true));
        sections.add(new ContextSection("resource_context", "resource_context / 可用资源",
                request.resourceContext(),
                60,
                true));
        sections.add(new ContextSection("rag_context", "rag_context / 检索上下文",
                request.ragContext(),
                65,
                true));
        return sections;
    }

    private List<String> recentTopicContents(String sessionKey) {
        if (graphRetriever == null) {
            return List.of();
        }
        List<MemoryGraphNode> nodes = graphRetriever.recentTopics(sessionKey, 5);
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(MemoryGraphNode::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }

    private List<String> longTermMemories(ContextBuildRequest request) {
        if (longTermMemoryRetriever == null) {
            return List.of();
        }
        List<String> values = longTermMemoryRetriever.longTermMemories(request.sessionKey(), request.userText(), 5);
        return values == null ? List.of() : values;
    }

    private List<MemoryGraphNode> activeExtractNodes(ContextBuildRequest request, ConversationRelevanceDecision decision) {
        if (graphRetriever == null || decision.currentTopic().isBlank()) {
            return List.of();
        }
        List<MemoryGraphNode> nodes = graphRetriever.activeExtracts(request.sessionKey(), decision.currentTopic(), 5);
        return nodes == null ? List.of() : nodes;
    }

    private List<String> formatTurns(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(turn -> "用户：" + turn.userText() + System.lineSeparator() + "助手：" + turn.assistantText())
                .toList();
    }

    private String nodeContents(List<MemoryGraphNode> nodes) {
        return nodes == null ? "" : String.join(System.lineSeparator(), nodes.stream()
                .map(MemoryGraphNode::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList());
    }

    private List<MemoryGraphNode> selectedNodes(ContextBuildRequest request, ConversationRelevanceDecision decision) {
        if (decision.relevance() != RelevanceLevel.STRONG) {
            return List.of();
        }
        return activeExtractNodes(request, decision);
    }

    private String renderSections(List<ContextSection> sections) {
        return String.join(System.lineSeparator(), (sections == null ? List.<ContextSection>of() : sections).stream()
                .map(ContextSection::formatted)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String policyText(RelevanceLevel relevance) {
        if (relevance == RelevanceLevel.STRONG) {
            return "保留最近 " + properties.strongRecentTurns() + " 轮完整对话 + 当前主题活摘 + 长期记忆";
        }
        return "保留最近 " + properties.weakRecentTurns() + " 轮完整对话 + 历史主题 + 长期记忆";
    }
}
