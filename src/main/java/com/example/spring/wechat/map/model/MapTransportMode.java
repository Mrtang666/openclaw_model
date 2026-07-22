package com.example.spring.wechat.map.model;

import java.util.Locale;

public enum MapTransportMode {
    ALL("all", "综合"),
    DRIVING("driving", "驾车"),
    TRANSIT("transit", "公共交通"),
    WALKING("walking", "步行");

    private final String value;
    private final String displayName;

    MapTransportMode(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    public static MapTransportMode from(String value) {
        String normalized = value == null || value.isBlank()
                ? ALL.value
                : value.strip().toLowerCase(Locale.ROOT);
        for (MapTransportMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new MapServiceException("不支持的交通方式：" + value);
    }
}
