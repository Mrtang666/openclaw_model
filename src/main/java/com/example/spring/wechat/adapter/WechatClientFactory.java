package com.example.spring.wechat.adapter;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
public interface WechatClientFactory {

    WechatClient create();
}

