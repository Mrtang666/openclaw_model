package com.example.spring.wechat.memory.model;

/**
 * 一轮已完成的微信文本对话，用于拼装后续模型调用的短期上下文。
 */
public record ConversationTurn(String userText, String assistantText) {
}
