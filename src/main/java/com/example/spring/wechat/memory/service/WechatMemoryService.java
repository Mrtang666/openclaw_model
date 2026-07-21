package com.example.spring.wechat.memory.service;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import com.example.spring.wechat.memory.model.WechatMemorySession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 微信端上下文记忆的统一入口，屏蔽正式 MySQL 存储与故障期内存兜底的差异。
 */
public interface WechatMemoryService {

    WechatMemorySession open(String wechatUserId, Instant now);

    boolean acceptIncoming(
            String wechatUserId,
            String sourceMessageId,
            String content,
            String contentType,
            Instant now);

    WechatConversationMemory memoryFor(String wechatUserId);

    void saveMemory(String wechatUserId, WechatConversationMemory memory, Instant now);

    void recordAssistantMessage(String wechatUserId, String content, String contentType, Instant now);

    void recordToolExecution(
            String wechatUserId,
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            String status,
            Instant now);

    /**
     * 生成超时会话摘要和长会话滚动摘要；内存兜底实现不需要持久化维护。
     */
    default void maintainConversationSummaries(Instant now) {
    }

    Optional<String> explicitPreference(String wechatUserId, String preferenceKey);

    void saveExplicitPreference(
            String wechatUserId,
            String preferenceKey,
            String valueJson,
            String source,
            Instant now);
}
