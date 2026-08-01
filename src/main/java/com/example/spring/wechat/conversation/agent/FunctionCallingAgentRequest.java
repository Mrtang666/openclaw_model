package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.conversation.WechatConversationMode;
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
        String ragContext,
        List<WechatIncomingFile> files,
        List<WechatIncomingImage> images,
        List<WechatIncomingVideo> videos,
        WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
        WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
        ToolExecutionRecorder toolExecutionRecorder,
        WechatConversationMode conversationMode) {

    public FunctionCallingAgentRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        historyText = historyText == null ? "" : historyText;
        ragContext = ragContext == null ? "" : ragContext;
        files = files == null ? List.of() : List.copyOf(files);
        images = images == null ? List.of() : List.copyOf(images);
        videos = videos == null ? List.of() : List.copyOf(videos);
        conversationMode = conversationMode == null ? WechatConversationMode.GENERAL : conversationMode;
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            String ragContext,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            List<WechatIncomingVideo> videos,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
            ToolExecutionRecorder toolExecutionRecorder) {
        this(
                sessionKey,
                userText,
                historyText,
                ragContext,
                files,
                images,
                videos,
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder,
                WechatConversationMode.GENERAL);
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            List<WechatIncomingVideo> videos,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
            ToolExecutionRecorder toolExecutionRecorder,
            WechatConversationMode conversationMode) {
        this(
                sessionKey,
                userText,
                historyText,
                "",
                files,
                images,
                videos,
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder,
                conversationMode);
    }

    public FunctionCallingAgentRequest(
            String sessionKey,
            String userText,
            String historyText,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images,
            List<WechatIncomingVideo> videos,
            WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
            WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
            ToolExecutionRecorder toolExecutionRecorder) {
        this(
                sessionKey,
                userText,
                historyText,
                "",
                files,
                images,
                videos,
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder,
                WechatConversationMode.GENERAL);
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
                "",
                files,
                List.of(),
                List.of(),
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder,
                WechatConversationMode.GENERAL);
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
                "",
                files,
                images,
                List.of(),
                pendingImagePromptRecorder,
                generatedImageRecorder,
                toolExecutionRecorder,
                WechatConversationMode.GENERAL);
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
                "",
                files,
                images,
                List.of(),
                pendingImagePromptRecorder,
                null,
                toolExecutionRecorder,
                WechatConversationMode.GENERAL);
    }


    @FunctionalInterface
    public interface ToolExecutionRecorder {
        void record(String toolName, Map<String, String> arguments, String resultSummary, String status);
    }
}
