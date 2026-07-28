package com.example.spring.wechat.travel.model;

public record MeituanTravelResult(String content) {

    public MeituanTravelResult {
        content = content == null ? "" : content.strip();
    }
}
