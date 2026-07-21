package com.example.spring.tool.protocol.function;

import java.util.List;

/**
 * Function Calling 模型单轮响应。
 *
 * <p>如果 {@code toolCalls} 非空，表示模型要求 Java 先执行工具；
 * 如果没有工具调用，则 {@code content} 就是最终 assistant 回复。</p>
 */
public record FunctionCallingModelResponse(String content, List<FunctionCallingToolCall> toolCalls) {

    public FunctionCallingModelResponse {
        content = content == null ? "" : content.strip();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
