package com.example.spring.exception;


/**
 * 业务异常定义，负责统一表示各模块的错误。
 */
public class WeatherServiceException extends RuntimeException {

    public WeatherServiceException(String message) {
        super(message);
    }
}


