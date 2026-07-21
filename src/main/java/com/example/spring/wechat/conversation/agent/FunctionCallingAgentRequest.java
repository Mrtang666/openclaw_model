package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import com.example.spring.wechat.model.WechatIncomingFile;

import java.util.List;
import java.util.Map;

/**
 * 完整 Function Calling Agent Loop 的单次微信请求上下文。
 *
 * <p>它把用户文本、历史上下文、文件附件以及记忆回调集中放在一起，
 * 让 Agent Loop 可以在不依赖 {@code WechatConversationService} 内部状态的情况下执行工具。</p>
 */
public record FunctionCallingAgentRequest(
        String sessionKey,
        String userText,
        String historyText,
        List<WechatIncomingFile> files,
        WechatToolRequest.PendingImagePromptRecorder pendingImagePromptRecorder,
        WechatToolRequest.GeneratedImageRecorder generatedImageRecorder,
        ToolExecutionRecorder toolExecutionRecorder) {

    public FunctionCallingAgentRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        historyText = historyText == null ? "" : historyText;
        files = files == null ? List.of() : List.copyOf(files);
    }

    @FunctionalInterface
    public interface ToolExecutionRecorder {
        void record(String toolName, Map<String, String> arguments, String resultSummary, String status);
    }
}
