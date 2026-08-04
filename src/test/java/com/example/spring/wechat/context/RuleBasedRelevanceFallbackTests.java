package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRelevanceFallbackTests {

    private final RuleBasedRelevanceFallback fallback = new RuleBasedRelevanceFallback();

    @Test
    void treatsShortReferencesAsStrong() {
        ConversationRelevanceDecision decision = fallback.classify(
                "继续",
                List.of("用户：我们在设计 Memory Graph\n助手：好的"),
                List.of("OpenClaw Memory Graph 上下文设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(decision.reason()).contains("指代");
    }

    @Test
    void treatsClearlyDifferentTopicAsWeak() {
        ConversationRelevanceDecision decision = fallback.classify(
                "今天杭州天气怎么样",
                List.of("用户：帮我设计 Java 单测\n助手：可以"),
                List.of("Spring Boot 单元测试设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.WEAK);
    }
}
