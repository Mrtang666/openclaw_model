package com.example.spring.xhs.schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class XhsReportScheduleCalculatorTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void calculatesNextDailyWeeklyAndMonthlyRuns() {
        Instant after = Instant.parse("2026-07-30T02:00:00Z"); // Thursday 10:00 in Shanghai

        assertThat(XhsReportScheduleCalculator.next("DAILY", LocalTime.of(9, 0), null, null,
                SHANGHAI, after)).isEqualTo(Instant.parse("2026-07-31T01:00:00Z"));
        assertThat(XhsReportScheduleCalculator.next("WEEKLY", LocalTime.of(9, 0), 1, null,
                SHANGHAI, after)).isEqualTo(Instant.parse("2026-08-03T01:00:00Z"));
        assertThat(XhsReportScheduleCalculator.next("MONTHLY", LocalTime.of(9, 0), null, 31,
                SHANGHAI, after)).isEqualTo(Instant.parse("2026-07-31T01:00:00Z"));
    }

    @Test
    void clampsMonthlyRunToLastDayOfShortMonth() {
        Instant after = Instant.parse("2026-02-01T00:00:00Z");

        assertThat(XhsReportScheduleCalculator.next("MONTHLY", LocalTime.of(9, 0), null, 31,
                SHANGHAI, after)).isEqualTo(Instant.parse("2026-02-28T01:00:00Z"));
    }
}
