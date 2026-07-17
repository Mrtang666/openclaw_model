package com.example.spring.wechat.image.service;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.wechat.image.ImageUnderstandingException;
import com.example.spring.wechat.image.client.ImageUnderstandingClient;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import org.springframework.stereotype.Service;

@Service
public class DefaultImageUnderstandingService implements ImageUnderstandingService {

    private final ImageInputResolver resolver;
    private final ImageUnderstandingClient client;

    public DefaultImageUnderstandingService(ImageInputResolver resolver, ImageUnderstandingClient client) {
        this.resolver = resolver;
        this.client = client;
    }

    @Override
    public String reply(WechatIncomingMessage message) {
        StringBuilder output = new StringBuilder();
        streamReply(message, output::append);
        return output.toString().strip();
    }

    @Override
    public void streamReply(WechatIncomingMessage message, ReplyEmitter emitter) {
        if (emitter == null) {
            throw new ImageUnderstandingException("缺少流式输出处理器");
        }

        ImageAnalysisRequest request = resolver.resolve(message);
        if (!request.hasImages()) {
            throw new ImageUnderstandingException("没有找到可识别的图片");
        }

        client.streamReply(request, emitter);
    }
}
