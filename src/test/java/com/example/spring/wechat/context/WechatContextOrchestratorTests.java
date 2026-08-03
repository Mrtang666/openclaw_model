package com.example.spring.wechat.context;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatContextOrchestratorTests {

    @Test
    void strongRelevanceKeepsFiveRecentTurnsAndActiveExtract() {
        WechatConversationMemory memory = WechatConversationMemory.empty(20, "更早摘要");
        for (int index = 1; index <= 11; index++) {
            memory.record("user-" + index, "assistant-" + index);
        }
        ConversationRelevanceClassifier classifier = mock(ConversationRelevanceClassifier.class);
        when(classifier.classify(anyString(), anyList(), anyList()))
                .thenReturn(ConversationRelevanceDecision.strong("topic-a", "same topic"));
        MemoryGraphRetriever graphRetriever = mock(MemoryGraphRetriever.class);
        when(graphRetriever.activeExtracts("session", "topic-a", 5))
                .thenReturn(List.of(node(MemoryNodeType.ACTIVE_EXTRACT, "topic-a", "活摘内容")));
        LongTermMemoryRetriever longTermRetriever = mock(LongTermMemoryRetriever.class);
        when(longTermRetriever.longTermMemories("session", "继续", 5))
                .thenReturn(List.of("长期偏好"));

        WechatContextOrchestrator orchestrator = orchestrator(classifier, graphRetriever, longTermRetriever);

        WechatContextPackage context = orchestrator.build(new ContextBuildRequest(
                "session", "继续", memory, "资源", "", null));

        assertThat(context.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(context.finalContextText())
                .contains("user-7")
                .contains("user-11")
                .doesNotContain("user-6")
                .contains("活摘内容")
                .contains("长期偏好");
    }

    @Test
    void weakRelevanceKeepsOneRecentTurnAndHistoricalTopics() {
        WechatConversationMemory memory = WechatConversationMemory.empty(20, "更早摘要");
        memory.record("旧主题", "旧回复");
        memory.record("上一轮", "上一轮回复");
        ConversationRelevanceClassifier classifier = mock(ConversationRelevanceClassifier.class);
        when(classifier.classify(anyString(), anyList(), anyList()))
                .thenReturn(ConversationRelevanceDecision.weak("new topic"));
        MemoryGraphRetriever graphRetriever = mock(MemoryGraphRetriever.class);
        when(graphRetriever.recentTopics("session", 5))
                .thenReturn(List.of(node(MemoryNodeType.CONVERSATION_TOPIC, "topic-b", "历史主题内容")));

        WechatContextOrchestrator orchestrator = orchestrator(
                classifier,
                graphRetriever,
                mock(LongTermMemoryRetriever.class));

        WechatContextPackage context = orchestrator.build(new ContextBuildRequest(
                "session", "新话题", memory, "", "", null));

        assertThat(context.relevance()).isEqualTo(RelevanceLevel.WEAK);
        assertThat(context.finalContextText())
                .contains("上一轮")
                .doesNotContain("旧主题")
                .contains("历史主题内容");
    }

    private WechatContextOrchestrator orchestrator(
            ConversationRelevanceClassifier classifier,
            MemoryGraphRetriever graphRetriever,
            LongTermMemoryRetriever longTermRetriever) {
        WechatContextProperties properties = new WechatContextProperties(
                true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8);
        ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();
        return new WechatContextOrchestrator(
                properties,
                classifier,
                graphRetriever,
                longTermRetriever,
                new ContextBudgetManager(properties, estimator),
                new ContextCompressor(estimator),
                new WechatContextAssembler());
    }

    private MemoryGraphNode node(MemoryNodeType type, String topic, String content) {
        return new MemoryGraphNode(1, "session", 1L, type, topic, topic, content, "",
                1, 1, 1, null, null, "", "", "", null, null, null, false);
    }
}
