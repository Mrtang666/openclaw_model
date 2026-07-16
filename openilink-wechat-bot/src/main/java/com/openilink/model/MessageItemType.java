package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageItemType {
    TEXT(1),
    IMAGE(2),
    VOICE(3),
    VIDEO(4),
    FILE(5),
    SYSTEM_TEXT(6),
    UNRECOGNIZED(-1);

    private final int value;

    MessageItemType(int value) { this.value = value; }

    @JsonValue
    public int getValue() { return value; }

    public static MessageItemType fromValue(int value) {
        for (MessageItemType t : values()) { if (t.value == value) return t; }
        return UNRECOGNIZED;
    }
}
