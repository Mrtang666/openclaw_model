package com.example.spring.wechat.care.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CarePlanTimeParserTests {

    private final CarePlanTimeParser parser = new CarePlanTimeParser();

    @Test
    void extractsNumericAndChineseClockTimesWithPeriods() {
        assertThat(parser.extractTimePoints("每天08:30服药，下午3点半训练，晚上8点确认，二十一点休息"))
                .containsExactly(
                        LocalTime.of(8, 30), LocalTime.of(15, 30),
                        LocalTime.of(20, 0), LocalTime.of(21, 0));
    }

    @Test
    void expandsExplicitHourlyRangeWithoutInventingTimesOutsideRange() {
        assertThat(parser.resolveDailyTimes("每天8点到20点每2小时进行安全确认", LocalTime.of(9, 0)))
                .containsExactly(
                        LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(12, 0),
                        LocalTime.of(14, 0), LocalTime.of(16, 0), LocalTime.of(18, 0),
                        LocalTime.of(20, 0));
    }

    @Test
    void ignoresRelativeTimeAndUsesTheProvidedFallback() {
        assertThat(parser.resolveDailyTimes("晚些时候进行训练", LocalTime.of(16, 0)))
                .containsExactly(LocalTime.of(16, 0));
    }
}
