package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageState {
    BOT(0),
    USER_INITIATE(1),
    FINISH(2);

    private final int value;

    MessageState(int value) { this.value = value; }

    @JsonValue
    public int getValue() { return value; }

    public static MessageState fromValue(int value) {
        for (MessageState s : values()) { if (s.value == value) return s; }
        return BOT;
    }
}
