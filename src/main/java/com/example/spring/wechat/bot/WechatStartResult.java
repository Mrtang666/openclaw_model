package com.example.spring.wechat.bot;

public record WechatStartResult(boolean started, String message, String qrCodeContent) {
}
