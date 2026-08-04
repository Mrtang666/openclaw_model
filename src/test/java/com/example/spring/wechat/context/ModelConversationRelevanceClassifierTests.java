package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelConversationRelevanceClassifierTests {

    @Test
    void parsesStrongModelDecision() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("只输出 JSON"))).thenReturn("""
                {"relevance":"STRONG","confidence":0.91,"currentTopic":"Memory Graph","reason":"same topic","relatedTopics":["Memory Graph"]}
                """);
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8, false, false));

        ConversationRelevanceDecision decision = classifier.classify(
                "continue discussing",
                List.of("user: Memory Graph design\nassistant: keep graph context"),
                List.of("Memory Graph"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(decision.confidence()).isEqualTo(0.91);
        assertThat(decision.currentTopic()).isEqualTo("Memory Graph");
    }

    @Test
    void fallsBackWhenModelThrows() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("只输出 JSON"))).thenThrow(new IllegalStateException("model down"));
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8, false, false));

        ConversationRelevanceDecision decision = classifier.classify("it?", List.of(), List.of("topic"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.WEAK);
    }

    @Test
    void skipsModelCallForObviousStrongTopicOverlap() {
        ChatService chatService = mock(ChatService.class);
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify(
                "Memory Graph performance",
                List.of("user: Memory Graph design\nassistant: keep graph context"),
                List.of("Memory Graph"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
        verifyNoInteractions(chatService);
    }

    @Test
    void skipsModelCallForObviousNewTopic() {
        ChatService chatService = mock(ChatService.class);
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify(
                "weather tomorrow",
                List.of("user: Memory Graph design\nassistant: keep graph context"),
                List.of("Memory Graph"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.WEAK);
        verifyNoInteractions(chatService);
    }
}
