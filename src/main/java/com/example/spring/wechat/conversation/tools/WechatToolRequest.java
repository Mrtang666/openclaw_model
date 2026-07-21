package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.model.WechatIncomingFile;

import java.util.List;
import java.util.Map;

public record WechatToolRequest(
        String sessionKey,
        String userText,
        Map<String, String> arguments,
        String historyText,
        List<WechatIncomingVoice> voices,
        List<WechatIncomingFile> files,
        PendingImagePromptRecorder pendingImagePromptRecorder,
        GeneratedImageRecorder generatedImageRecorder) {

    public WechatToolRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        historyText = historyText == null ? "" : historyText;
        voices = voices == null ? List.of() : List.copyOf(voices);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            List<WechatIncomingVoice> voices,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, voices, List.of(), pendingImagePromptRecorder, generatedImageRecorder);
    }

    public WechatToolRequest(
            String sessionKey,
            String userText,
            Map<String, String> arguments,
            String historyText,
            PendingImagePromptRecorder pendingImagePromptRecorder,
            GeneratedImageRecorder generatedImageRecorder) {
        this(sessionKey, userText, arguments, historyText, List.of(), List.of(), pendingImagePromptRecorder, generatedImageRecorder);
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
                || "是".equals(value)
                || "需要".equals(value);
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

    @FunctionalInterface
    public interface PendingImagePromptRecorder {
        void record(String userText, String prompt);
    }

    @FunctionalInterface
    public interface GeneratedImageRecorder {
        void record(String userText, String prompt);
    }
}
