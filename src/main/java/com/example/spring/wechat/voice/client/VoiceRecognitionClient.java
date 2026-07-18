package com.example.spring.wechat.voice.client;

import com.example.spring.wechat.voice.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;

public interface VoiceRecognitionClient {

    VoiceRecognitionResult recognize(VoiceRecognitionRequest request);
}
