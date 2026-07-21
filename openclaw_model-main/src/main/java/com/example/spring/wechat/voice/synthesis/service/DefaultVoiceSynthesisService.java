package com.example.spring.wechat.voice.synthesis.service;

import com.example.spring.wechat.voice.recognition.audio.AudioTranscoder;
import com.example.spring.wechat.voice.synthesis.exception.VoiceSynthesisException;
import com.example.spring.wechat.voice.synthesis.client.VoiceSynthesisClient;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisAudio;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisRequest;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 语音合成服务：把文本按微信语音时长上限拆段，再合成为可发送的 silk 语音片段。
 */
@Service
public class DefaultVoiceSynthesisService implements VoiceSynthesisService {

    private static final int WECHAT_SILK_ENCODE_TYPE = 6;
    private static final int BITS_PER_SAMPLE = 16;

    private final VoiceSynthesisClient client;
    private final AudioTranscoder audioTranscoder;
    private final int maxWechatVoiceDurationMs;
    private final int sampleRate;
    private final double estimatedCharsPerSecond;
    private final String model;
    private final String voice;
    private final String providerFormat;

    @Autowired
    public DefaultVoiceSynthesisService(
            VoiceSynthesisClient client,
            AudioTranscoder audioTranscoder,
            @Value("${voice.tts.max-wechat-duration-ms:58000}") int maxWechatVoiceDurationMs,
            @Value("${voice.tts.sample-rate:16000}") int sampleRate,
            @Value("${voice.tts.estimated-chars-per-second:4.0}") double estimatedCharsPerSecond,
            @Value("${openclaw.dashscope.tts-model:${dashscope.tts-model:qwen3-tts-flash}}") String model,
            @Value("${dashscope.tts-voice:Cherry}") String voice,
            @Value("${dashscope.tts-format:wav}") String providerFormat) {
        this.client = client;
        this.audioTranscoder = audioTranscoder;
        this.maxWechatVoiceDurationMs = Math.min(60_000, Math.max(5_000, maxWechatVoiceDurationMs));
        this.sampleRate = sampleRate <= 0 ? 16000 : sampleRate;
        this.estimatedCharsPerSecond = estimatedCharsPerSecond <= 0 ? 4.0 : estimatedCharsPerSecond;
        this.model = model == null || model.isBlank() ? "qwen3-tts-flash" : model.strip();
        this.voice = voice == null || voice.isBlank() ? "Cherry" : voice.strip();
        this.providerFormat = providerFormat == null || providerFormat.isBlank()
                ? "wav"
                : providerFormat.strip().toLowerCase(Locale.ROOT);
    }

    public DefaultVoiceSynthesisService(
            VoiceSynthesisClient client,
            AudioTranscoder audioTranscoder,
            int maxWechatVoiceDurationMs,
            int sampleRate,
            double estimatedCharsPerSecond) {
        this(
                client,
                audioTranscoder,
                maxWechatVoiceDurationMs,
                sampleRate,
                estimatedCharsPerSecond,
                "qwen3-tts-flash",
                "Cherry",
                "wav");
    }

    @Override
    public List<VoiceSynthesisSegment> synthesizeForWechat(String text) {
        return synthesizeForWechat(text, voice);
    }

    @Override
    public List<VoiceSynthesisSegment> synthesizeForWechat(String text, String selectedVoice) {
        if (text == null || text.isBlank()) {
            throw new VoiceSynthesisException("语音合成文本不能为空");
        }

        String effectiveVoice = selectedVoice == null || selectedVoice.isBlank() ? voice : selectedVoice.strip();
        List<String> textSegments = splitTextForWechatDuration(text.strip());
        List<VoiceSynthesisSegment> voiceSegments = new ArrayList<>();
        for (int index = 0; index < textSegments.size(); index++) {
            String segmentText = textSegments.get(index);
            VoiceSynthesisAudio audio = client.synthesize(new VoiceSynthesisRequest(
                    segmentText,
                    model,
                    effectiveVoice,
                    providerFormat,
                    sampleRate));
            try {
                voiceSegments.add(new VoiceSynthesisSegment(
                        toSilk(audio),
                        "reply-" + (index + 1) + ".silk",
                        "silk",
                        "audio/silk",
                        estimateDurationMs(segmentText),
                        sampleRate,
                        WECHAT_SILK_ENCODE_TYPE,
                        BITS_PER_SAMPLE,
                        segmentText));
            } catch (RuntimeException exception) {
                voiceSegments.add(providerAudioSegment(audio, segmentText, index));
            }
        }
        return voiceSegments;
    }

    private byte[] toSilk(VoiceSynthesisAudio audio) {
        if (audio == null || audio.audioBytes() == null || audio.audioBytes().length == 0) {
            throw new VoiceSynthesisException("语音合成结果为空");
        }
        if ("silk".equalsIgnoreCase(audio.format())) {
            return audio.audioBytes();
        }
        if (audioTranscoder == null) {
            throw new VoiceSynthesisException("语音回复需要转换为 silk，但转码服务未配置");
        }
        return audioTranscoder.convertToSilk(audio.audioBytes(), audio.format(), audio.sampleRate());
    }

    private VoiceSynthesisSegment providerAudioSegment(VoiceSynthesisAudio audio, String segmentText, int index) {
        String format = audio.format();
        return new VoiceSynthesisSegment(
                audio.audioBytes(),
                "reply-" + (index + 1) + "." + format,
                format,
                audio.contentType(),
                estimateDurationMs(segmentText),
                audio.sampleRate(),
                null,
                null,
                segmentText);
    }

    private List<String> splitTextForWechatDuration(String text) {
        int maxChars = Math.max(20, (int) Math.floor(maxWechatVoiceDurationMs / 1000.0 * estimatedCharsPerSecond));
        if (text.length() <= maxChars) {
            return List.of(text);
        }

        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + maxChars);
            int end = bestBoundary(text, start, hardEnd);
            String segment = text.substring(start, end).strip();
            if (!segment.isBlank()) {
                segments.add(segment);
            }
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return segments;
    }

    private int bestBoundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        for (int index = hardEnd - 1; index > start + 10; index--) {
            char ch = text.charAt(index);
            if ("。！？!?；;，,\n".indexOf(ch) >= 0) {
                return index + 1;
            }
        }
        return hardEnd;
    }

    private int estimateDurationMs(String text) {
        int charCount = Math.max(1, text == null ? 0 : text.strip().length());
        return Math.min(maxWechatVoiceDurationMs, Math.max(1000, (int) Math.ceil(charCount / estimatedCharsPerSecond * 1000)));
    }
}
