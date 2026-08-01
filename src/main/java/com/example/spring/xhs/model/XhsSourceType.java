package com.example.spring.xhs.model;

public enum XhsSourceType {
    AUTHORIZED_API,
    DATA_PROVIDER,
    SPIDER_XHS_LAB,
    FILE_IMPORT;

    public static XhsSourceType from(String value) {
        if (value == null || value.isBlank()) {
            return FILE_IMPORT;
        }
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FILE_IMPORT;
        }
    }
}
