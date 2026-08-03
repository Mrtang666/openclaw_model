package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConversationRelevanceClassifierTests {

    @Test
    void parsesStrongModelDecision() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("只输出 JSON"))).thenReturn("""
                {"relevance":"STRONG","confidence":0.91,"currentTopic":"Memory Graph","reason":"同一主题","relatedTopics":["Memory Graph"]}
                """);
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify(
                "继续讲活摘",
                List.of("用户：Memory Graph 怎么做\n助手：我们拆成节点"),
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
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify("继续", List.of(), List.of("上下文设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
    }
}
