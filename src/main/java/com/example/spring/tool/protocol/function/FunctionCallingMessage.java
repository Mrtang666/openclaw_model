package com.example.spring.tool.protocol.function;

import java.util.List;

/**
 * OpenAI-compatible chat/completions 消息对象。
 *
 * <p>标准 Function Calling 循环中会出现四类消息：
 * system/user 普通文本消息、assistant 携带 tool_calls 的消息、tool 携带工具执行结果的消息。</p>
 */
public record FunctionCallingMessage(
        String role,
        String content,
        List<FunctionCallingToolCall> toolCalls,
        String toolCallId) {

    public FunctionCallingMessage {
        role = role == null ? "" : role.strip();
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = toolCallId == null ? "" : toolCallId.strip();
    }

    public static FunctionCallingMessage system(String content) {
        return new FunctionCallingMessage("system", content, List.of(), "");
    }

    public static FunctionCallingMessage user(String content) {
        return new FunctionCallingMessage("user", content, List.of(), "");
    }

    public static FunctionCallingMessage assistant(String content) {
        return new FunctionCallingMessage("assistant", content, List.of(), "");
    }

    public static FunctionCallingMessage assistantToolCalls(List<FunctionCallingToolCall> toolCalls) {
        return new FunctionCallingMessage("assistant", "", toolCalls, "");
    }

    public static FunctionCallingMessage tool(String toolCallId, String content) {
        return new FunctionCallingMessage("tool", content, List.of(), toolCallId);
    }
}
