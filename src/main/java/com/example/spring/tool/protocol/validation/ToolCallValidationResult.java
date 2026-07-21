package com.example.spring.tool.protocol.validation;

/**
 * 工具调用参数校验结果。
 *
 * <p>valid=true 表示可以继续执行工具；valid=false 表示当前工具调用不安全或参数不完整，
 * message 会说明需要补充或修正的内容，供 Agent Loop 回传给模型继续追问用户。</p>
 */
public record ToolCallValidationResult(boolean valid, String message) {

    public ToolCallValidationResult {
        message = message == null ? "" : message.strip();
    }

    public static ToolCallValidationResult ok() {
        return new ToolCallValidationResult(true, "");
    }

    public static ToolCallValidationResult invalid(String message) {
        return new ToolCallValidationResult(false, message);
    }
}
