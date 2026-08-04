package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WechatContextPropertiesTests {

    @Test
    void normalizesInvalidValuesToSafeDefaults() {
        WechatContextProperties properties = new WechatContextProperties(
                true,
                true,
                true,
                0,
                -3,
                0,
                0,
                -1,
                0,
                -100,
                -200,
                0.0);

        assertThat(properties.memoryGraphEnabled()).isTrue();
        assertThat(properties.relevanceClassifierEnabled()).isTrue();
        assertThat(properties.longTermMemoryIngestionEnabled()).isTrue();
        assertThat(properties.strongRecentTurns()).isEqualTo(5);
        assertThat(properties.weakRecentTurns()).isEqualTo(1);
        assertThat(properties.minRecentTurns()).isEqualTo(2);
        assertThat(properties.summaryWindowSize()).isEqualTo(5);
        assertThat(properties.summaryOverlapTurns()).isEqualTo(1);
        assertThat(properties.modelWindowTokens()).isEqualTo(128_000);
        assertThat(properties.outputReserveTokens()).isEqualTo(8_000);
        assertThat(properties.toolLoopReserveTokens()).isEqualTo(12_000);
        assertThat(properties.maxInputRatio()).isEqualTo(0.8);
    }
}
