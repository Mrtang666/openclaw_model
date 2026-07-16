package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TypingStatus {
    Typing(0),
    Recording(1),
    Stop(2),
    Cancel(3);

    private final int value;

    TypingStatus(int value) { this.value = value; }

    @JsonValue
    public int getValue() { return value; }

    public static TypingStatus fromValue(int value) {
        for (TypingStatus s : values()) { if (s.value == value) return s; }
        return Typing;
    }
}
