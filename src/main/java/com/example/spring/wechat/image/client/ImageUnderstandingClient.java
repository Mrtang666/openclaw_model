package com.example.spring.wechat.image.client;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;

public interface ImageUnderstandingClient {

    String reply(ImageAnalysisRequest request);

    void streamReply(ImageAnalysisRequest request, ReplyEmitter emitter);
}
