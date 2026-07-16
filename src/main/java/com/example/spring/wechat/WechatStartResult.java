package com.example.spring.wechat;

public record WechatStartResult(boolean started, String message, String qrCodeContent) {
}
