package com.example.spring.wechat.reminder.model;

import java.util.Locale;

public enum ReminderRepeatType {
    ONCE,
    DAILY,
    WEEKLY;

    public static ReminderRepeatType from(String value) {
        if (value == null || value.isBlank()) {
            return ONCE;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ReminderException("repeat_type 只能是 once、daily 或 weekly");
        }
    }
}
