package com.example.spring.wechat.reminder.model;

import java.time.Duration;
import java.util.Locale;

public enum ReminderDelayUnit {
    MINUTES,
    HOURS,
    DAYS;

    public static ReminderDelayUnit from(String value) {
        if (value == null || value.isBlank()) {
            return MINUTES;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ReminderException("delay_unit 只能是 minutes、hours 或 days");
        }
    }

    public Duration duration(long value) {
        return switch (this) {
            case MINUTES -> Duration.ofMinutes(value);
            case HOURS -> Duration.ofHours(value);
            case DAYS -> Duration.ofDays(value);
        };
    }
}
