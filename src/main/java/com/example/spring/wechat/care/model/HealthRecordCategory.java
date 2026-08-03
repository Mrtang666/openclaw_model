package com.example.spring.wechat.care.model;

import java.util.Locale;

public enum HealthRecordCategory {
    BLOOD_PRESSURE,
    BLOOD_GLUCOSE,
    TEMPERATURE,
    HEART_RATE,
    OXYGEN_SATURATION,
    WEIGHT,
    MEDICATION,
    SYMPTOM,
    SAFETY_STATUS,
    OTHER;

    public static HealthRecordCategory from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OTHER;
        }
    }
}
