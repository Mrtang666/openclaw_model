package com.example.spring.chat;


/**
 * 文本大模型接入层组件，负责封装模型请求、响应和异常。
 */
public record ChatReply(String reasoningContent, String content) {
}

