package com.example.spring.wechat.image.service;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.client.WechatIncomingMessage;

public interface ImageUnderstandingService {

    String reply(WechatIncomingMessage message);

    void streamReply(WechatIncomingMessage message, ReplyEmitter emitter);
}
