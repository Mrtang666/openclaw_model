package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingVideo;

import java.util.List;
import java.util.Map;

public record FunctionCallingAgentRequest(
        String sessionKey,
        String userText,
        String historyText,
        List<WechatIncomingFile> files,
        List<WechatIncomingImage> images,
        List<WechatIncomingVideo> videos,
        WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
        WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
        ToolExecutionRecorder toolExecutionRecorder) {

    public FunctionCallingAgentRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        historyText = historyText == null ? "" : historyText;
        files = files == null ? List.of() : List.copyOf(files);
        images = images == null ? List.of() : List.copyOf(images);
        videos = videos == null ? List.of() : List.copyOf(videos);
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            List<WechatIncomingFile> files,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
            ToolExecutionRecorder toolExecutionRecorder) {
        this(
                sessionKey,
                userText,
                historyText,
                files,
                List.of(),
                List.of(),
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder);
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
            ToolExecutionRecorder toolExecutionRecorder) {
        this(
                sessionKey,
                userText,
                historyText,
                files,
                images,
                List.of(),
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder);
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            ToolExecutionRecorder toolExecutionRecorder) {
        this(
                sessionKey,
                userText,
                historyText,
                files,
                images,
                List.of(),
                pendingImagePromptRecorder,
                null,
                toolExecutionRecorder);
    }


    @FunctionalInterface
    public interface ToolExecutionRecorder {
        void record(String toolName, Map<String, String> arguments, String resultSummary, String status);
    }
}
