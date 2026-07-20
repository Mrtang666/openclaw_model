package com.example.spring.exception;


/**
 * 业务异常定义，负责统一表示各模块的错误。
 */
public class CommandException extends RuntimeException {

    public CommandException(String message) {
        super(message);
    }
}


