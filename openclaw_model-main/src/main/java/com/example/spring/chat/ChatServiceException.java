package com.example.spring.chat;


/**
 * 文本大模型接入层组件，负责封装模型请求、响应和异常。
 */
public class ChatServiceException extends RuntimeException {

    public ChatServiceException(String message) {
        super(message);
    }

    public ChatServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

