package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextCompressorTests {

    private final ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();

    @Test
    void compressesLowerPrioritySectionsBeforeRecentTurns() {
        ContextCompressor compressor = new ContextCompressor(estimator);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "recent_turns", "R".repeat(80), 10, true),
                new ContextSection("long_term_memory", "long_term_memory", "L".repeat(200), 80, true),
                new ContextSection("active_extract", "active_extract", "A".repeat(160), 60, true));

        List<ContextSection> compressed = compressor.compress(sections, 180);

        String joined = compressed.stream().map(ContextSection::content).reduce("", String::concat);
        assertThat(estimator.estimate(joined)).isLessThanOrEqualTo(180);
        assertThat(compressed.get(0).content()).isEqualTo("R".repeat(80));
        assertThat(joined).contains("[已压缩");
    }

    @Test
    void skipsSemanticCompressionByDefault() {
        SectionCompressionService semanticCompression = mock(SectionCompressionService.class);
        ContextCompressor compressor = new ContextCompressor(estimator, semanticCompression);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "recent_turns", "R".repeat(40), 10, true),
                new ContextSection("long_term_memory", "long_term_memory", "L".repeat(200), 80, true));

        List<ContextSection> compressed = compressor.compress(sections, 90);

        assertThat(compressed.get(0).content()).isEqualTo("R".repeat(40));
        assertThat(compressed.get(1).content()).contains("[已压缩");
        verifyNoInteractions(semanticCompression);
    }

    @Test
    void usesSemanticCompressionWhenEnabled() {
        SectionCompressionService semanticCompression = mock(SectionCompressionService.class);
        when(semanticCompression.compressSection(eq("long_term_memory"), eq("L".repeat(200)), anyInt()))
                .thenReturn("long_term_memory summary");
        ContextCompressor compressor = new ContextCompressor(estimator, semanticCompression, true);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "recent_turns", "R".repeat(40), 10, true),
                new ContextSection("long_term_memory", "long_term_memory", "L".repeat(200), 80, true));

        List<ContextSection> compressed = compressor.compress(sections, 90);

        assertThat(compressed.get(0).content()).isEqualTo("R".repeat(40));
        assertThat(compressed.get(1).content()).isEqualTo("long_term_memory summary");
        assertThat(compressed.stream().map(ContextSection::content).reduce("", String::concat))
                .doesNotContain("[已压缩");
    }

    @Test
    void fallsBackToTruncationWhenSemanticCompressionFails() {
        SectionCompressionService semanticCompression = mock(SectionCompressionService.class);
        when(semanticCompression.compressSection(eq("long_term_memory"), eq("L".repeat(200)), anyInt()))
                .thenThrow(new IllegalStateException("model down"));
        ContextCompressor compressor = new ContextCompressor(estimator, semanticCompression, true);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "recent_turns", "R".repeat(40), 10, true),
                new ContextSection("long_term_memory", "long_term_memory", "L".repeat(200), 80, true));

        List<ContextSection> compressed = compressor.compress(sections, 90);

        assertThat(compressed.get(0).content()).isEqualTo("R".repeat(40));
        assertThat(compressed.get(1).content()).contains("[已压缩");
        assertThat(estimator.estimate(compressed.stream().map(ContextSection::content).reduce("", String::concat)))
                .isLessThanOrEqualTo(90);
    }

    @Test
    void truncatesSemanticCompressionWhenSummaryStillExceedsBudget() {
        SectionCompressionService semanticCompression = mock(SectionCompressionService.class);
        when(semanticCompression.compressSection(eq("long_term_memory"), eq("L".repeat(200)), anyInt()))
                .thenReturn("S".repeat(120));
        ContextCompressor compressor = new ContextCompressor(estimator, semanticCompression, true);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "recent_turns", "R".repeat(40), 10, true),
                new ContextSection("long_term_memory", "long_term_memory", "L".repeat(200), 80, true));

        List<ContextSection> compressed = compressor.compress(sections, 90);

        String joined = compressed.stream().map(ContextSection::content).reduce("", String::concat);
        assertThat(estimator.estimate(joined)).isLessThanOrEqualTo(90);
        assertThat(compressed.get(1).content()).contains("[已压缩");
    }
}
