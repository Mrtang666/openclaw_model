package com.example.spring.wechat.image.service;


/**
 * 微信图片处理服务层，负责图片输入解析和理解流程。
 */
import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.model.WechatIncomingMessage;

public interface ImageUnderstandingService {

    String reply(WechatIncomingMessage message);

    void streamReply(WechatIncomingMessage message, ReplyEmitter emitter);
}

