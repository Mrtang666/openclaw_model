package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageType {
    BOT(0),
    USER(1),
    SYSTEM(2);

    private final int value;

    MessageType(int value) { this.value = value; }

    @JsonValue
    public int getValue() { return value; }

    public static MessageType fromValue(int value) {
        for (MessageType t : values()) { if (t.value == value) return t; }
        return BOT;
    }
}
