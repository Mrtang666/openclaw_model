package com.example.spring.wechat.voice.recognition.audio;


/**
 * 微信语音音频处理层，负责格式检测和转码。
 */
public interface AudioTranscoder {

    byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate);

    byte[] convertToSilk(byte[] inputBytes, String inputFormat, Integer sampleRate);
}

