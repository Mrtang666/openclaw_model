package com.example.spring.xhs.incident;

import java.util.Locale;
import java.util.Set;

public enum XhsIncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED;

    public static XhsIncidentStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("target_status 不能为空");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的事件状态：" + value, exception);
        }
    }

    public boolean canTransitionTo(XhsIncidentStatus target) {
        if (target == this) {
            return true;
        }
        return switch (this) {
            case OPEN -> Set.of(ACKNOWLEDGED, INVESTIGATING).contains(target);
            case ACKNOWLEDGED -> target == INVESTIGATING;
            case INVESTIGATING -> target == RESOLVED;
            case RESOLVED -> target == INVESTIGATING;
        };
    }
}
