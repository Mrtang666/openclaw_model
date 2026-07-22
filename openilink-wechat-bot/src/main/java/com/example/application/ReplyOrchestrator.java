package com.example.application;

import com.example.adapter.wechat.WechatMessageSender;
import com.example.tts.TextToSpeechService;
import com.example.voice.SilkVoiceService;
import com.example.voice.VoiceProfileService;
import com.github.wechat.ilink.sdk.ILinkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Chooses the user-facing reply transport and owns voice fallback policy. */
public class ReplyOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReplyOrchestrator.class);
    private static final Pattern IMAGE_MARKER = Pattern.compile("(?is)\\[?\\s*\\**\\s*IMAGE_GEN\\s*[:：].*");
    private static final Pattern UNSUPPORTED_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final int MAX_TEXT_MESSAGE_CHARS = 1400;
    private static final int MIN_RETRY_SPLIT_CHARS = 240;
    private static final int MAX_SPLIT_RETRY_DEPTH = 4;

    private final WechatMessageSender sender;
    private final TextToSpeechService tts;
    private final SilkVoiceService silk;
    private final VoiceProfileService voices;
    private final Map<String, Boolean> voiceModes;

    public ReplyOrchestrator(WechatMessageSender sender,
                             TextToSpeechService tts,
                             SilkVoiceService silk,
                             VoiceProfileService voices,
                             Map<String, Boolean> voiceModes) {
        this.sender = sender;
        this.tts = tts;
        this.silk = silk;
        this.voices = voices;
        this.voiceModes = voiceModes;
    }

    public void reply(ILinkClient client, String userId, String text) {
        if (userId == null || text == null) return;
        String safeText = sanitize(text);
        if (safeText.isBlank()) return;
        if (isVoiceEnabled(userId)) {
            replyByVoiceOrFallback(client, userId, safeText);
            return;
        }
        sendText(client, userId, safeText);
    }

    public void sendText(ILinkClient client, String userId, String text) {
        String safeText = normalizeTextForWechat(text);
        if (userId == null || safeText.isBlank()) {
            return;
        }
        List<String> parts = splitTextForWechat(safeText);
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (parts.size() > 1) {
                part = "（" + (i + 1) + "/" + parts.size() + "）\n" + part;
            }
            sendTextPart(client, userId, part, 0);
        }
    }

    public boolean isVoiceEnabled(String userId) {
        return userId != null && Boolean.TRUE.equals(voiceModes.get(userId));
    }

    private void replyByVoiceOrFallback(ILinkClient client, String userId, String text) {
        String speechText = prepareSpeechText(text);
        if (speechText.isBlank()) {
            sendVoiceFailureFallback(client, userId, text, "回复内容为空");
            return;
        }

        String selectedVoice = voices.getVoice(userId);
        TextToSpeechService.TtsResult mp3 = tts.synthesize(speechText, "mp3", selectedVoice);
        String mp3Failure = sendAudioFile(client, userId, mp3, "mp3");
        if (mp3Failure == null) return;

        log.warn("MP3 语音回复失败，开始降级 WAV: {}", mp3Failure);
        TextToSpeechService.TtsResult wav = tts.synthesize(speechText, "wav", selectedVoice);
        String wavFailure = sendAudioFile(client, userId, wav, "wav");
        if (wavFailure == null) return;

        sendVoiceFailureFallback(client, userId, text,
                "MP3失败: " + mp3Failure + "；WAV失败: " + wavFailure);
    }

    private String sendAudioFile(ILinkClient client, String userId,
                                 TextToSpeechService.TtsResult result, String format) {
        if (result == null || !result.isSuccess()) {
            return result == null ? "TTS未返回结果" : result.getMessage();
        }
        byte[] audio = "mp3".equalsIgnoreCase(format)
                ? result.getAudioBytes() : silk.toWavBytes(result);
        if (audio == null || audio.length == 0) return format.toUpperCase() + "音频数据为空";

        String fileName = "voice_reply_" + System.currentTimeMillis() + "." + format.toLowerCase();
        saveDebugFile(fileName, audio);
        try {
            sender.sendFile(client, userId, audio, fileName, null);
            log.info("已发送微信可播放音频文件: user={}, file={}, format={}, bytes={}, sampleRate={}",
                    userId, fileName, format, audio.length, result.getSampleRate());
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? "文件发送异常" : e.getMessage();
        }
    }

    private void sendVoiceFailureFallback(ILinkClient client, String userId, String text, String reason) {
        sendText(client, userId, "语音回复失败，已改用文字回复。原因：" + reason + "\n" + text);
    }

    private String prepareSpeechText(String text) {
        String speech = text.replaceAll("(?is)\\[\\s*IMAGE_GEN\\s*[:：].*?]", "")
                .replaceAll("(?is)\\*\\*+", "")
                .replaceAll("`+", "")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return speech.length() > 1200 ? speech.substring(0, 1200).trim() : speech;
    }

    private String sanitize(String text) {
        return IMAGE_MARKER.matcher(text).replaceAll("").replaceAll("\\*+$", "").trim();
    }

    private String normalizeTextForWechat(String text) {
        if (text == null) {
            return "";
        }
        return UNSUPPORTED_CONTROL_CHARS.matcher(text)
                .replaceAll("")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private List<String> splitTextForWechat(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_TEXT_MESSAGE_CHARS);
            if (end < text.length()) {
                end = findTextBoundary(text, start, end);
            }
            if (end <= start) {
                end = Math.min(text.length(), start + MAX_TEXT_MESSAGE_CHARS);
            }
            String part = text.substring(start, end).trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return parts;
    }

    private int findTextBoundary(String text, int start, int preferredEnd) {
        String punctuation = "\n。！？!?；;，,、 ";
        for (int i = preferredEnd - 1; i > start + 200; i--) {
            if (punctuation.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return preferredEnd;
    }

    private void sendTextPart(ILinkClient client, String userId, String text, int depth) {
        try {
            sender.sendText(client, userId, text);
            log.info("已文字回复用户 [{}]: {}", userId, text);
        } catch (Exception e) {
            if (text.length() > MIN_RETRY_SPLIT_CHARS && depth < MAX_SPLIT_RETRY_DEPTH) {
                int boundary = findTextBoundary(text, 0, text.length() / 2);
                if (boundary <= 0 || boundary >= text.length()) {
                    boundary = text.length() / 2;
                }
                log.warn("文字回复失败，尝试继续拆分发送: length={}, depth={}, error={}",
                        text.length(), depth, e.getMessage());
                sendTextPart(client, userId, text.substring(0, boundary).trim(), depth + 1);
                sendTextPart(client, userId, text.substring(boundary).trim(), depth + 1);
                return;
            }
            log.warn("文字回复失败: length={}, error={}", text.length(), e.getMessage());
        }
    }

    private void saveDebugFile(String fileName, byte[] bytes) {
        try {
            Path directory = Paths.get("downloads", "voice_reply");
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), bytes);
        } catch (Exception e) {
            log.debug("保存语音调试文件失败: {}", e.getMessage());
        }
    }
}
