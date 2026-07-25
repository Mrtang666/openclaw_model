package com.example.spring.wechat.bot;


/**
 * 微信 Bot 运行与发送层，负责登录、队列和消息发送。
 */
public record WechatBotStatus(
        WechatBotState state,
        String botId,
        String lastError,
        int connectedCount,
        int pendingCount,
        int totalConnections) {

    public WechatBotStatus(WechatBotState state, String botId, String lastError) {
        this(state, botId, lastError,
                state == WechatBotState.RUNNING ? 1 : 0,
                state == WechatBotState.WAITING_FOR_SCAN ? 1 : 0,
                state == WechatBotState.STOPPED ? 0 : 1);
    }
}

