package com.example.spring.wechat.bot;


/**
 * 微信 Bot 运行与发送层，负责登录、队列和消息发送。
 */
public enum WechatBotState {
    STOPPED,
    WAITING_FOR_SCAN,
    RUNNING,
    ERROR
}

