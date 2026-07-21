package com.example.spring.wechat.voice.synthesis.client;

import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisAudio;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisRequest;

public interface VoiceSynthesisClient {

    VoiceSynthesisAudio synthesize(VoiceSynthesisRequest request);
}
