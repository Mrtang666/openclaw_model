package com.example.spring.tool.protocol.legacy;


/**
 * 它表示“一个具体工具调用”。
 * 标准工具调用协议层，负责承载和解析大模型工具计划。
 */
import java.util.Map;

public record ToolCall(String tool, Map<String, String> arguments) {

    public ToolCall {
        tool = tool == null ? "" : tool.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}

