package com.example.spring.wechat.bot.multiclient;

import com.example.spring.wechat.bot.WechatBotState;

import java.time.Instant;

public record ClawBotConnectionSnapshot(
        String connectionId,
        String displayName,
        WechatBotState state,
        ClawBotProcessingState processingState,
        String botId,
        String loginSessionId,
        Instant createdAt,
        Instant lastActivityAt,
        int queuedMessages,
        int activeMessages,
        String lastError,
        String requestedRole) {

    public ClawBotConnectionSnapshot(
            String connectionId,
            String displayName,
            WechatBotState state,
            ClawBotProcessingState processingState,
            String botId,
            String loginSessionId,
            Instant createdAt,
            Instant lastActivityAt,
            int queuedMessages,
            int activeMessages,
            String lastError) {
        this(connectionId, displayName, state, processingState, botId, loginSessionId,
                createdAt, lastActivityAt, queuedMessages, activeMessages, lastError, "");
    }
}
