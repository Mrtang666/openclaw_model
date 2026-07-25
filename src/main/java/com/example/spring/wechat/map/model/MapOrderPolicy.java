package com.example.spring.wechat.map.model;

import java.util.Locale;

public enum MapOrderPolicy {
    PRESERVE("preserve"),
    OPTIMIZE("optimize");

    private final String value;

    MapOrderPolicy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MapOrderPolicy from(String value) {
        String normalized = value == null || value.isBlank()
                ? PRESERVE.value
                : value.strip().toLowerCase(Locale.ROOT);
        for (MapOrderPolicy policy : values()) {
            if (policy.value.equals(normalized)) {
                return policy;
            }
        }
        throw new MapServiceException("不支持的路线排序方式：" + value);
    }
}
