package com.example.spring.wechat.voice.recognition.client;


/**
 * 微信语音识别客户端层，负责调用外部识别模型。
 */
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;

public interface VoiceRecognitionClient {

    VoiceRecognitionResult recognize(VoiceRecognitionRequest request);
}

