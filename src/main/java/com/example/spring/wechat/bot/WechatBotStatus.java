package com.example.spring.wechat.bot;

public record WechatBotStatus(WechatBotState state, String botId, String lastError) {
}
