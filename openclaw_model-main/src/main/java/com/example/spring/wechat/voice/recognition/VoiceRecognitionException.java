package com.example.spring.wechat.voice.recognition;


/**
 * 微信语音识别模块的异常定义。
 */
public class VoiceRecognitionException extends RuntimeException {

    public VoiceRecognitionException(String message) {
        super(message);
    }

    public VoiceRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

