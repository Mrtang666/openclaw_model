package com.example.spring.wechat.care.exception;

import org.springframework.http.HttpStatus;

public enum CareErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    CONFIGURATION_ERROR(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus httpStatus;

    CareErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
