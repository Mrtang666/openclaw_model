package com.example.spring.wechat.care.rules;

import com.example.spring.wechat.care.model.DailyCheckIn;
import com.example.spring.wechat.care.model.SafetySeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SafetyRuleEngine {

    private static final List<String> URGENT_KEYWORDS = List.of("救命", "无法呼吸", "呼吸困难", "胸痛", "昏迷");
    private static final List<String> WANDERING_KEYWORDS = List.of("迷路", "找不到家", "不知道在哪里");

    public List<AlertCandidate> evaluate(DailyCheckIn checkIn) {
        List<AlertCandidate> candidates = new ArrayList<>();
        String incident = clean(checkIn.incidentType()).toUpperCase(Locale.ROOT);
        String text = clean(checkIn.originalText());
        if ("FALL".equals(incident) || text.contains("跌倒") || text.contains("摔倒")) {
            candidates.add(new AlertCandidate("FALL_REPORTED", SafetySeverity.URGENT, "患者签到报告发生跌倒"));
        }
        if ("WANDERING".equals(incident) || containsAny(text, WANDERING_KEYWORDS)) {
            candidates.add(new AlertCandidate("POSSIBLE_WANDERING", SafetySeverity.URGENT, "患者签到出现迷路或走失相关信息"));
        }
        if (containsAny(text, URGENT_KEYWORDS)) {
            candidates.add(new AlertCandidate("EXPLICIT_DISTRESS", SafetySeverity.URGENT, "患者签到出现明确紧急求助信息"));
        }
        return List.copyOf(candidates);
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public record AlertCandidate(String alertType, SafetySeverity severity, String evidenceText) {
    }
}
