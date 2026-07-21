package com.example.spring.wechat.image.client;


/**
 * 微信图片理解客户端层，负责调用视觉模型。
 */
import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;

public interface ImageUnderstandingClient {

    String reply(ImageAnalysisRequest request);

    void streamReply(ImageAnalysisRequest request, ReplyEmitter emitter);
}

