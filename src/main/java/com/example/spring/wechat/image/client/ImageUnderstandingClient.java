package com.example.spring.wechat.image.client;


/**
 * 微信图片理解客户端层，负责调用视觉模型。
 */
import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;

public interface ImageUnderstandingClient {

    String reply(ImageAnalysisRequest request);

    default Response replyWithUsage(ImageAnalysisRequest request) {
        return new Response(reply(request), "", 0, 0, 0, 0);
    }

    void streamReply(ImageAnalysisRequest request, ReplyEmitter emitter);

    record Response(String content, String model, int promptTokens, int completionTokens,
                    int totalTokens, long durationMs) {
    }
}

