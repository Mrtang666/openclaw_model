package com.example.spring.wechat.care.rules;

import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.SafetySeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyRuleEngineTests {

    private final SafetyRuleEngine engine = new SafetyRuleEngine();

    @Test
    void fallReportCreatesUrgentCandidate() {
        DailyCheckIn checkIn = checkIn("FALL", "今天在家中摔倒了");

        assertThat(engine.evaluate(checkIn))
                .anySatisfy(candidate -> {
                    assertThat(candidate.alertType()).isEqualTo("FALL_REPORTED");
                    assertThat(candidate.severity()).isEqualTo(SafetySeverity.URGENT);
                });
    }

    @Test
    void ordinaryCheckInDoesNotCreateAlert() {
        assertThat(engine.evaluate(checkIn("", "今天状态正常"))).isEmpty();
    }

    private DailyCheckIn checkIn(String incidentType, String text) {
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        return new DailyCheckIn(
                1L, 10L, 10L, LocalDate.of(2026, 7, 29), "GOOD", "NORMAL", "NORMAL",
                "CALM", "NORMAL", true, incidentType, text, "WECHAT", "DONE",
                "checkin:10:2026-07-29", 0L, now, now, now);
    }
}
