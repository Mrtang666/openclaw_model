package com.example.spring.wechat.video.model;

import com.example.spring.wechat.model.WechatIncomingVideo;

import java.util.List;

public record VideoUnderstandingRequest(String instruction, List<WechatIncomingVideo> videos) {

    public VideoUnderstandingRequest {
        instruction = instruction == null || instruction.isBlank() ? "请描述这个视频。" : instruction.strip();
        videos = videos == null ? List.of() : List.copyOf(videos);
    }

    public boolean hasVideos() {
        return !videos.isEmpty();
    }
}
