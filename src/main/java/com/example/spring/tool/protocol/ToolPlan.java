package com.example.spring.tool.protocol;


/**
 * 它是 ToolCall 的集合。
 * 标准工具调用协议层，负责承载和解析大模型工具计划。
 */
import java.util.List;

public record ToolPlan(List<ToolCall> tasks) {

    public ToolPlan {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}

