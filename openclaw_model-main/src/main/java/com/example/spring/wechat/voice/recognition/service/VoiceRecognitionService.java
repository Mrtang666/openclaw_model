package com.example.spring.wechat.voice.recognition.service;


/**
 * 微信语音识别服务层，负责语音转文字流程。
 */
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;

public interface VoiceRecognitionService {

    VoiceRecognitionResult recognize(WechatIncomingVoice voice);
}

