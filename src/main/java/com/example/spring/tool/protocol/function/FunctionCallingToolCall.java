package com.example.spring.tool.protocol.function;

import java.util.Map;

/**
 * Function Calling 返回的一次工具调用。
 *
 * <p>和项目原来的 {@code ToolCall} 相比，这里额外保留了模型返回的 tool_call id。
 * 在标准 Agent Loop 中，工具执行结果必须用这个 id 作为 {@code tool_call_id}
 * 回传给模型，模型才能知道哪条工具结果对应哪次调用。</p>
 */
public record FunctionCallingToolCall(String id, String name, Map<String, String> arguments) {

    public FunctionCallingToolCall {
        id = id == null || id.isBlank() ? "call_" + java.util.UUID.randomUUID() : id.strip();
        name = name == null ? "" : name.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
