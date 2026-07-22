package com.example.spring.wechat.bot;


/**
 * 微信 Bot 运行与发送层，负责登录、队列和消息发送。
 */
public record WechatStartResult(boolean started, String message, String loginPageUrl) {
}

