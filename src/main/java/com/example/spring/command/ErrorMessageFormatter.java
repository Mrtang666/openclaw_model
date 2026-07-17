package com.example.spring.command;

import com.example.spring.exception.CommandException;
import com.example.spring.exception.WeatherServiceException;
import com.example.spring.chat.ChatServiceException;

final class ErrorMessageFormatter {

    private static final String DEFAULT_SYSTEM_ERROR = "程序执行失败，请稍后重试";

    private ErrorMessageFormatter() {
    }

    static String format(Exception exception) {
        if (exception instanceof CommandException) {
            return "输入有问题：" + messageOrDefault(exception, "命令参数不正确");
        }

        if (exception instanceof WeatherServiceException) {
            return "天气服务异常：" + messageOrDefault(exception, "天气查询失败");
        }

        if (exception instanceof ChatServiceException) {
            return "大模型异常：" + messageOrDefault(exception, "对话生成失败");
        }

        return "系统异常：" + rootMessageOrDefault(exception);
    }

    private static String messageOrDefault(Throwable exception, String defaultMessage) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }
        return message;
    }

    private static String rootMessageOrDefault(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        return messageOrDefault(current, DEFAULT_SYSTEM_ERROR);
    }
}
