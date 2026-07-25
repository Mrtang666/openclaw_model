package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingVideo;
import com.example.spring.wechat.model.WechatIncomingVoice;

import java.util.List;
import java.util.Map;

public record WechatToolRequest(
        String sessionKey,
        String userText,
        Map<String, String> arguments,
        String historyText,
        List<WechatIncomingVoice> voices,
        List<WechatIncomingFile> files,
        List<WechatIncomingImage> images,
        List<WechatIncomingVideo> videos,
        PendingImagePromptRecorder pendingImagePromptRecorder,
        GeneratedImageRecorder generatedImageRecorder) {

    public WechatToolRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        historyText = historyText == null ? "" : historyText;
        voices = voices == null ? List.of() : List.copyOf(voices);
        files = files == null ? List.of() : List.copyOf(files);
        images = images == null ? List.of() : List.copyOf(images);
        videos = videos == null ? List.of() : List.copyOf(videos);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            List<WechatIncomingVoice> voices,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, voices, List.of(), List.of(), List.of(), pendingImagePromptRecorder, generatedImageRecorder);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            List<WechatIncomingVoice> voices,
            List<WechatIncomingFile> files,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, voices, files, List.of(), List.of(), pendingImagePromptRecorder, generatedImageRecorder);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            List<WechatIncomingVoice> voices,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, voices, files, images, List.of(), pendingImagePromptRecorder, generatedImageRecorder);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, List.of(), List.of(), List.of(), List.of(), pendingImagePromptRecorder, generatedImageRecorder);
    }

    public String argument(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return arguments.getOrDefault(name, "").strip();
    }

    public boolean booleanArgument(String name) {
        String value = argument(name);
        return "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "1".equals(value)
                || "鏄?".equals(value)
                || "闇€瑕?".equals(value);
    }

    public void rememberPendingImagePrompt(String prompt) {
        if (pendingImagePromptRecorder != null) {
            pendingImagePromptRecorder.record(userText, prompt);
        }
    }

    public void rememberGeneratedImage(String prompt) {
        if (generatedImageRecorder != null) {
            generatedImageRecorder.record(userText, prompt);
        }
    }

    public boolean hasVideos() {
        return !videos.isEmpty();
    }

    @FunctionalInterface
    public interface PendingImagePromptRecorder {
        void record(String userText, String prompt);
    }

    @FunctionalInterface
    public interface GeneratedImageRecorder {
        void record(String userText, String prompt);
    }
}
