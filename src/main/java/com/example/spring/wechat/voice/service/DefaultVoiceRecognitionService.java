package com.example.spring.wechat.voice.service;

import com.example.spring.wechat.client.WechatIncomingVoice;
import com.example.spring.wechat.voice.VoiceRecognitionException;
import com.example.spring.wechat.voice.audio.AudioFormatDetector;
import com.example.spring.wechat.voice.audio.AudioTranscoder;
import com.example.spring.wechat.voice.client.VoiceRecognitionClient;
import com.example.spring.wechat.voice.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultVoiceRecognitionService implements VoiceRecognitionService {

    private final VoiceRecognitionClient client;
    private final AudioFormatDetector audioFormatDetector;
    private final AudioTranscoder audioTranscoder;

    public DefaultVoiceRecognitionService(VoiceRecognitionClient client) {
        this(client, new AudioFormatDetector(), null);
    }

    public DefaultVoiceRecognitionService(VoiceRecognitionClient client, AudioFormatDetector audioFormatDetector) {
        this(client, audioFormatDetector, null);
    }

    @Autowired
    public DefaultVoiceRecognitionService(
            VoiceRecognitionClient client,
            AudioFormatDetector audioFormatDetector,
            AudioTranscoder audioTranscoder) {
        this.client = client;
        this.audioFormatDetector = audioFormatDetector;
        this.audioTranscoder = audioTranscoder;
    }

    @Override
    public VoiceRecognitionResult recognize(WechatIncomingVoice voice) {
        if (voice == null) {
            throw new VoiceRecognitionException("语音消息不能为空");
        }

        if (voice.hasEmbeddedText()) {
            return new VoiceRecognitionResult(
                    voice.embeddedText().strip(),
                    "zh",
                    null,
                    voice.durationMs(),
                    "WECHAT_EMBEDDED");
        }

        if (!voice.hasBytes() && !voice.hasSourceUrl()) {
            throw new VoiceRecognitionException("没有读取到语音内容，请重新发送一次");
        }

        String format = audioFormatDetector.detect(voice.fileName(), voice.mimeType(), voice.format());
        PreparedAudio preparedAudio = prepareAudio(voice, format);
        VoiceRecognitionResult result = client.recognize(new VoiceRecognitionRequest(
                preparedAudio.audioBytes(),
                voice.sourceUrl(),
                preparedAudio.fileName(),
                preparedAudio.contentType(),
                preparedAudio.format(),
                voice.sampleRate(),
                voice.durationMs(),
                "zh"));
        if (result == null || result.text() == null || result.text().isBlank()) {
            throw new VoiceRecognitionException("我没有听清楚，可以再说一遍吗？");
        }
        return result;
    }

    private PreparedAudio prepareAudio(WechatIncomingVoice voice, String format) {
        if (!voice.hasBytes()) {
            return new PreparedAudio(null, voice.fileName(), voice.mimeType(), format);
        }

        if (isAsrFriendlyFormat(format)) {
            return new PreparedAudio(voice.bytes(), voice.fileName(), voice.mimeType(), format);
        }

        if (audioTranscoder == null) {
            throw new VoiceRecognitionException("语音格式 " + format + " 需要转换，但转码服务暂未配置");
        }

        byte[] wavBytes = audioTranscoder.convertToWav(voice.bytes(), format, voice.sampleRate());
        return new PreparedAudio(wavBytes, replaceExtensionWithWav(voice.fileName()), "audio/wav", "wav");
    }

    private boolean isAsrFriendlyFormat(String format) {
        return switch (format == null ? "" : format) {
            case "wav", "mp3", "m4a", "flac", "amr", "ogg", "opus" -> true;
            default -> false;
        };
    }

    private String replaceExtensionWithWav(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "voice.wav";
        }

        String normalized = fileName.strip();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0) {
            return normalized + ".wav";
        }
        return normalized.substring(0, dotIndex) + ".wav";
    }

    private record PreparedAudio(byte[] audioBytes, String fileName, String contentType, String format) {
    }
}
