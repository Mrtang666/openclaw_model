package com.example.spring.wechat.conversation.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTests {

    @Test
    void preservesExplicitValues() {
        RagProperties properties = new RagProperties(true, true, 7, 0.42, 4096, false);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.autoRetrieve()).isTrue();
        assertThat(properties.topK()).isEqualTo(7);
        assertThat(properties.minScore()).isEqualTo(0.42);
        assertThat(properties.maxContextChars()).isEqualTo(4096);
        assertThat(properties.includeSources()).isFalse();
    }

    @Test
    void normalizesUnsafeNumericValues() {
        RagProperties properties = new RagProperties(false, false, 0, -1, 0, true);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.autoRetrieve()).isFalse();
        assertThat(properties.topK()).isEqualTo(5);
        assertThat(properties.minScore()).isEqualTo(0.2);
        assertThat(properties.maxContextChars()).isEqualTo(6000);
        assertThat(properties.includeSources()).isTrue();
    }
}
