package com.example.spring.wechat.care.exception;

public class CareException extends RuntimeException {

    private final CareErrorCode code;

    public CareException(CareErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CareErrorCode code() {
        return code;
    }
}
