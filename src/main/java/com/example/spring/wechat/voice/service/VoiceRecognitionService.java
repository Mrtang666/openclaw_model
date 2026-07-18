package com.example.spring.wechat.voice.service;

import com.example.spring.wechat.client.WechatIncomingVoice;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;

public interface VoiceRecognitionService {

    VoiceRecognitionResult recognize(WechatIncomingVoice voice);
}
