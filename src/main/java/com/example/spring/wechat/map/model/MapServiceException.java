package com.example.spring.wechat.map.model;

public class MapServiceException extends RuntimeException {

    public MapServiceException(String message) {
        super(message);
    }

    public MapServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
