package com.example.spring.wechat.memory.fallback;

import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import com.example.spring.wechat.memory.model.WechatMemorySession;
import com.example.spring.wechat.memory.service.WechatMemoryService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MySQL 临时不可用时使用的进程内微信记忆。
 *
 * <p>该实现只保证当前进程不中断；应用重启后这些临时数据会自然丢失。</p>
 */
@Component
public class InMemoryWechatMemoryFallback implements WechatMemoryService {

    private final WechatMemoryProperties properties;
    private final AtomicLong idGenerator = new AtomicLong();
    private final Map<String, FallbackSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> preferences = new ConcurrentHashMap<>();
    private final Set<String> acceptedMessageIds = ConcurrentHashMap.newKeySet();

    public InMemoryWechatMemoryFallback(WechatMemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public WechatMemorySession open(String wechatUserId, Instant now) {
        String key = safeKey(wechatUserId);
        Instant time = now == null ? Instant.now() : now;
        FallbackSession session = sessions.compute(key, (ignored, current) -> {
            if (current == null || current.lastActive().plusSeconds(
                    properties.sessionIdleMinutes() * 60L).isBefore(time)) {
                return new FallbackSession(
                        idGenerator.incrementAndGet(),
                        idGenerator.incrementAndGet(),
                        WechatConversationMemory.empty(properties.recentTurnLimit()),
                        time);
            }
            current.lastActive(time);
            return current;
        });
        return new WechatMemorySession(session.userId(), session.conversationId(), session.memory());
    }

    @Override
    public boolean acceptIncoming(
            String wechatUserId,
            String sourceMessageId,
            String content,
            String contentType,
            Instant now) {
        open(wechatUserId, now);
        return sourceMessageId == null || sourceMessageId.isBlank()
                || acceptedMessageIds.add(sourceMessageId.strip());
    }

    @Override
    public WechatConversationMemory memoryFor(String wechatUserId) {
        return open(wechatUserId, Instant.now()).memory();
    }

    @Override
    public void saveMemory(String wechatUserId, WechatConversationMemory memory, Instant now) {
        FallbackSession current = sessions.get(safeKey(wechatUserId));
        if (current != null && memory != null) {
            current.memory(memory);
            current.lastActive(now == null ? Instant.now() : now);
        }
    }

    @Override
    public void recordAssistantMessage(String wechatUserId, String content, String contentType, Instant now) {
        open(wechatUserId, now);
    }

    @Override
    public void recordToolExecution(
            String wechatUserId,
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            String status,
            Instant now) {
        open(wechatUserId, now);
    }

    @Override
    public Optional<String> explicitPreference(String wechatUserId, String preferenceKey) {
        return Optional.ofNullable(preferences.get(preferenceMapKey(wechatUserId, preferenceKey)));
    }

    @Override
    public void saveExplicitPreference(
            String wechatUserId,
            String preferenceKey,
            String valueJson,
            String source,
            Instant now) {
        if (preferenceKey != null && !preferenceKey.isBlank() && valueJson != null && !valueJson.isBlank()) {
            preferences.put(preferenceMapKey(wechatUserId, preferenceKey), valueJson);
        }
    }

    private String safeKey(String userId) {
        return userId == null || userId.isBlank() ? "default" : userId.strip();
    }

    private String preferenceMapKey(String userId, String preferenceKey) {
        return safeKey(userId) + ":" + (preferenceKey == null ? "" : preferenceKey.strip());
    }

    private static final class FallbackSession {

        private final long userId;
        private final long conversationId;
        private WechatConversationMemory memory;
        private Instant lastActive;

        private FallbackSession(long userId, long conversationId, WechatConversationMemory memory, Instant lastActive) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.memory = memory;
            this.lastActive = lastActive;
        }

        private long userId() {
            return userId;
        }

        private long conversationId() {
            return conversationId;
        }

        private WechatConversationMemory memory() {
            return memory;
        }

        private void memory(WechatConversationMemory memory) {
            this.memory = memory;
        }

        private Instant lastActive() {
            return lastActive;
        }

        private void lastActive(Instant lastActive) {
            this.lastActive = lastActive;
        }
    }
}
