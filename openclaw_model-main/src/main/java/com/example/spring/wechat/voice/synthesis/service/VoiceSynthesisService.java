package com.example.spring.wechat.voice.synthesis.service;

import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;

import java.util.List;

public interface VoiceSynthesisService {

    List<VoiceSynthesisSegment> synthesizeForWechat(String text);

    default List<VoiceSynthesisSegment> synthesizeForWechat(String text, String voice) {
        return synthesizeForWechat(text);
    }
}
