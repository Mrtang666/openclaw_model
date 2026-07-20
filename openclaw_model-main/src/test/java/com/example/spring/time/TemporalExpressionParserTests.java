package com.example.spring.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TemporalExpressionParserTests {
    private final TemporalExpressionParser parser = new TemporalExpressionParser();
    private final LocalDate current = LocalDate.of(2026, 7, 20);

    @Test
    void resolvesRelativeAndAbsoluteDates() {
        assertThat(parser.parse("今天无锡天气", current).targetDate()).isEqualTo(current);
        assertThat(parser.parse("明天无锡天气", current).targetDate())
            .isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(parser.parse("后天无锡天气", current).targetDate())
            .isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(parser.parse("大后天无锡天气", current).targetDate())
            .isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(parser.parse("2026年7月25日天气", current).targetDate())
            .isEqualTo(LocalDate.of(2026, 7, 25));
    }

    @Test
    void resolvesWeekdaysAndRemovesDateExpression() {
        assertThat(parser.parse("下周一上海天气", current).targetDate())
            .isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(parser.removeDateExpressions("查询后天无锡滨湖区天气"))
            .doesNotContain("后天")
            .contains("无锡滨湖区");
    }
}
