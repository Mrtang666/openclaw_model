package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UploadMediaType {
    IMAGE(1),
    VIDEO(2),
    FILE(3),
    VOICE(4);

    private final int value;

    UploadMediaType(int value) { this.value = value; }

    @JsonValue
    public int getValue() { return value; }

    public static UploadMediaType fromValue(int value) {
        for (UploadMediaType t : values()) { if (t.value == value) return t; }
        return FILE;
    }
}
