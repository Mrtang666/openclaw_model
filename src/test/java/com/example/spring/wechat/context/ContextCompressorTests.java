package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCompressorTests {

    private final ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();

    @Test
    void compressesLowerPrioritySectionsBeforeRecentTurns() {
        ContextCompressor compressor = new ContextCompressor(estimator);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "最近完整对话", "R".repeat(80), 10, true),
                new ContextSection("long_term_memory", "长期记忆", "L".repeat(200), 80, true),
                new ContextSection("active_extract", "活摘", "A".repeat(160), 60, true));

        List<ContextSection> compressed = compressor.compress(sections, 180);

        String joined = compressed.stream().map(ContextSection::content).reduce("", String::concat);
        assertThat(estimator.estimate(joined)).isLessThanOrEqualTo(180);
        assertThat(compressed.get(0).content()).isEqualTo("R".repeat(80));
        assertThat(joined).contains("[已压缩]");
    }
}
