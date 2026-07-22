package com.example.spring.wechat.map.model;

import java.util.Locale;

public enum MapNearbyCategory {
    ALL("all", "周边地点", ""),
    FOOD("food", "附近美食", "050000"),
    ATTRACTION("attraction", "附近景点", "110000"),
    SHOPPING("shopping", "附近商场", "060000");

    private final String value;
    private final String displayName;
    private final String amapType;

    MapNearbyCategory(String value, String displayName, String amapType) {
        this.value = value;
        this.displayName = displayName;
        this.amapType = amapType;
    }

    public String value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    public String amapType() {
        return amapType;
    }

    public static MapNearbyCategory from(String value) {
        String normalized = value == null || value.isBlank()
                ? ALL.value
                : value.strip().toLowerCase(Locale.ROOT);
        for (MapNearbyCategory category : values()) {
            if (category.value.equals(normalized)) {
                return category;
            }
        }
        throw new MapServiceException("不支持的周边分类：" + value);
    }
}
