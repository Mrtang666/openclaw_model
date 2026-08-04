package com.example.spring.xhs.analysis;

import com.example.spring.xhs.model.XhsMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class XhsSemanticCacheTests {

    @Test
    void normalizesWhitespaceBeforeHashingContent() {
        XhsSemanticCache cache = new XhsSemanticCache(mock(JdbcTemplate.class), new ObjectMapper());
        XhsAnalysisCandidate first = candidate("标题", "正文  有空格\n和换行");
        XhsAnalysisCandidate second = candidate(" 标题 ", "正文 有空格 和换行");

        assertThat(cache.hash(first)).isEqualTo(cache.hash(second));
    }

    private XhsAnalysisCandidate candidate(String title, String content) {
        return new XhsAnalysisCandidate(1, 2, "brand", title, content, "https://example.com",
                Instant.EPOCH, Instant.EPOCH, XhsMetrics.empty());
    }
}
