package com.example.spring.wechat;

public record WechatIncomingMessage(String fromUserId, String text) {
}
