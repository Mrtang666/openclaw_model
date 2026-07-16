package com.example.spring.wechat;

public record WechatBotStatus(WechatBotState state, String botId, String lastError) {
}
