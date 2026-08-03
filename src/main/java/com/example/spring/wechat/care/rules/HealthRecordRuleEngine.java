package com.example.spring.wechat.care.rules;

import com.example.spring.wechat.care.model.HealthRecord;
import com.example.spring.wechat.care.model.HealthRecordCategory;
import com.example.spring.wechat.care.model.SafetySeverity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Conservative first-pass rules. They create a review alert, not a diagnosis. */
@Component
public class HealthRecordRuleEngine {

    private static final List<String> EMERGENCY_WORDS = List.of("胸痛", "胸闷", "呼吸困难", "无法呼吸", "昏迷", "救命");

    public SafetyRuleEngine.AlertCandidate evaluate(HealthRecord record) {
        BigDecimal value = record.primaryValue();
        BigDecimal second = record.secondaryValue();
        return switch (record.category()) {
            case BLOOD_PRESSURE -> bloodPressure(value, second);
            case TEMPERATURE -> threshold(value, new BigDecimal("37.3"), new BigDecimal("39.0"),
                    "TEMPERATURE_ELEVATED", "体温偏高");
            case HEART_RATE -> heartRate(value);
            case OXYGEN_SATURATION -> oxygen(value);
            case BLOOD_GLUCOSE -> glucose(record);
            case SYMPTOM, SAFETY_STATUS, OTHER -> emergencyText(record.recordText());
            default -> null;
        };
    }

    private SafetyRuleEngine.AlertCandidate bloodPressure(BigDecimal systolic, BigDecimal diastolic) {
        if (systolic == null || diastolic == null) return null;
        if (atLeast(systolic, 180) || atLeast(diastolic, 120)) {
            return urgent("BLOOD_PRESSURE_CRITICAL", "血压达到紧急关注阈值，请尽快联系医生");
        }
        if (atLeast(systolic, 140) || atLeast(diastolic, 90)) {
            return attention("BLOOD_PRESSURE_ELEVATED", "血压偏高，建议按医生要求复测并关注变化");
        }
        return null;
    }

    private SafetyRuleEngine.AlertCandidate heartRate(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(new BigDecimal("40")) < 0 || atLeast(value, 130)) {
            return urgent("HEART_RATE_CRITICAL", "心率达到紧急关注阈值，请尽快联系医生");
        }
        if (value.compareTo(new BigDecimal("50")) < 0 || atLeast(value, 100)) {
            return attention("HEART_RATE_ABNORMAL", "心率异常，建议复测并关注变化");
        }
        return null;
    }

    private SafetyRuleEngine.AlertCandidate oxygen(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(new BigDecimal("90")) < 0) {
            return urgent("OXYGEN_SATURATION_CRITICAL", "血氧饱和度偏低，请尽快联系医生");
        }
        if (value.compareTo(new BigDecimal("95")) < 0) {
            return attention("OXYGEN_SATURATION_LOW", "血氧饱和度偏低，建议复测并关注变化");
        }
        return null;
    }

    private SafetyRuleEngine.AlertCandidate glucose(HealthRecord record) {
        if (record.primaryValue() == null || !"mmol/L".equalsIgnoreCase(record.unit())) return null;
        return threshold(record.primaryValue(), new BigDecimal("11.1"), new BigDecimal("16.7"),
                "BLOOD_GLUCOSE_ELEVATED", "血糖偏高，建议按医生要求复测并关注变化");
    }

    private SafetyRuleEngine.AlertCandidate threshold(
            BigDecimal value, BigDecimal attentionThreshold, BigDecimal urgentThreshold, String type, String text) {
        if (value == null) return null;
        if (value.compareTo(urgentThreshold) >= 0) return urgent(type + "_URGENT", text);
        if (value.compareTo(attentionThreshold) >= 0) return attention(type, text);
        return null;
    }

    private SafetyRuleEngine.AlertCandidate emergencyText(String text) {
        String value = text == null ? "" : text.strip();
        return EMERGENCY_WORDS.stream().anyMatch(value::contains)
                ? urgent("EMERGENCY_SYMPTOM_REPORTED", "患者上报了紧急症状，请尽快联系患者") : null;
    }

    private boolean atLeast(BigDecimal value, int threshold) {
        return value.compareTo(BigDecimal.valueOf(threshold)) >= 0;
    }

    private SafetyRuleEngine.AlertCandidate attention(String type, String text) {
        return new SafetyRuleEngine.AlertCandidate(type, SafetySeverity.ATTENTION, text);
    }

    private SafetyRuleEngine.AlertCandidate urgent(String type, String text) {
        return new SafetyRuleEngine.AlertCandidate(type, SafetySeverity.URGENT, text);
    }
}
