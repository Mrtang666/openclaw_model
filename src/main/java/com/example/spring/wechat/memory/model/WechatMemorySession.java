package com.example.spring.wechat.memory.model;

/**
 * 一次微信消息处理期间关联的正式用户、会话和已加载记忆。
 */
public record WechatMemorySession(
        long userId,
        long conversationId,
        WechatConversationMemory memory) {
}
