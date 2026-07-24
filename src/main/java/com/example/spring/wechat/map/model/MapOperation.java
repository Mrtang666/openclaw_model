package com.example.spring.wechat.map.model;

import java.util.Locale;

public enum MapOperation {
    PLACE_SEARCH("place_search"),
    PLACE_DETAIL("place_detail"),
    ROUTE("route"),
    MULTI_ROUTE("multi_route"),
    NEARBY_SEARCH("nearby_search");

    private final String value;

    MapOperation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MapOperation from(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        for (MapOperation operation : values()) {
            if (operation.value.equals(normalized)) {
                return operation;
            }
        }
        throw new MapServiceException("不支持的地图操作：" + value);
    }
}
