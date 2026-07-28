package com.example.spring.wechat.video.client;

import com.example.spring.wechat.video.model.VideoUnderstandingRequest;

public interface VideoUnderstandingClient {

    String reply(VideoUnderstandingRequest request);
}
