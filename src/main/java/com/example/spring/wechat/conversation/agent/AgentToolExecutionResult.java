package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.bot.WechatReply;

import java.util.List;
import java.util.Map;

/**
 * Agent Loop 内部标准化工具执行结果。
 *
 * <p>modelText 是回传给大模型继续推理的摘要；visibleParts 是最终要交给微信发送层的媒体内容；
 * status 和 errorMessage 用于日志与数据库工具调用记录。</p>
 */
public record AgentToolExecutionResult(
        String toolName,
        Map<String, String> arguments,
        String modelText,
        List<WechatReply.Part> visibleParts,
        String status,
        String errorMessage) {

    public AgentToolExecutionResult {
        toolName = toolName == null ? "" : toolName.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        modelText = modelText == null ? "" : modelText.strip();
        visibleParts = visibleParts == null ? List.of() : List.copyOf(visibleParts);
        status = status == null || status.isBlank() ? "SUCCESS" : status.strip();
        errorMessage = errorMessage == null ? "" : errorMessage.strip();
    }

    public static AgentToolExecutionResult success(
            String toolName,
            Map<String, String> arguments,
            String modelText,
            List<WechatReply.Part> visibleParts) {
        return new AgentToolExecutionResult(toolName, arguments, modelText, visibleParts, "SUCCESS", "");
    }

    public static AgentToolExecutionResult failure(
            String toolName,
            Map<String, String> arguments,
            String modelText,
            String errorMessage) {
        return new AgentToolExecutionResult(toolName, arguments, modelText, List.of(), "FAILED", errorMessage);
    }
}
