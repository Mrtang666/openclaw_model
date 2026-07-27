package com.example.spring.wechat.travel.client;

public class MeituanTravelClientException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        AUTHENTICATION,
        TIMEOUT,
        NO_RESULT,
        OUTPUT_LIMIT,
        EXECUTION
    }

    private final Kind kind;

    public MeituanTravelClientException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public MeituanTravelClientException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
