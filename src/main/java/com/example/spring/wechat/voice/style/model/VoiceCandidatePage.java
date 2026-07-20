package com.example.spring.wechat.voice.style.model;

import java.util.List;

/**
 * 音色候选分页结果。
 * 微信消息不适合一次展示过长列表，所以每次只返回一小批候选，并记录是否还有下一批。
 */
public record VoiceCandidatePage(
        List<VoiceProfile> candidates,
        int page,
        boolean hasMore,
        String query) {

    public VoiceCandidatePage {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        page = Math.max(0, page);
        query = query == null ? "" : query.strip();
    }
}
