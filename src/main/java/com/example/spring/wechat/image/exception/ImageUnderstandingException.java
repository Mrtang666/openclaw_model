package com.example.spring.wechat.image.exception;


/**
 * 微信图片理解模块的异常定义。
 */
public class ImageUnderstandingException extends RuntimeException {

    public ImageUnderstandingException(String message) {
        super(message);
    }

    public ImageUnderstandingException(String message, Throwable cause) {
        super(message, cause);
    }
}

