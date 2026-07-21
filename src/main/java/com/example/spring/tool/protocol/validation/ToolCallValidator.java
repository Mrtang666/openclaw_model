package com.example.spring.tool.protocol.validation;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Function Calling 工具调用参数校验器。
 *
 * <p>大模型返回 tool_calls 后，先在这里做确定性校验，再决定是否真的执行工具。
 * 它负责拦截工具不存在、必填参数缺失、枚举值非法、布尔值格式不明确等问题。</p>
 */
@Component
public class ToolCallValidator {

    public ToolCallValidationResult validate(
            FunctionCallingToolCall toolCall,
            List<WechatToolDefinition> definitions) {
        if (toolCall == null || toolCall.name().isBlank()) {
            return ToolCallValidationResult.invalid("工具调用缺少工具名。");
        }

        Optional<WechatToolDefinition> definition = findDefinition(toolCall.name(), definitions);
        if (definition.isEmpty()) {
            return ToolCallValidationResult.invalid("工具不存在：" + toolCall.name());
        }

        Map<String, String> arguments = toolCall.arguments();
        for (WechatToolParameter parameter : definition.get().parameters()) {
            String value = arguments.getOrDefault(parameter.name(), "").strip();
            if (parameter.required() && value.isBlank()) {
                return ToolCallValidationResult.invalid("工具 " + toolCall.name() + " 缺少必填参数：" + parameter.name());
            }
            if (value.isBlank()) {
                continue;
            }
            if (!parameter.allowedValues().isEmpty()
                    && parameter.allowedValues().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(value))) {
                return ToolCallValidationResult.invalid("参数 " + parameter.name()
                        + " 的取值不合法，允许值为：" + String.join("、", parameter.allowedValues()));
            }
            if ("boolean".equalsIgnoreCase(parameter.type()) && !isBooleanLike(value)) {
                return ToolCallValidationResult.invalid("参数 " + parameter.name() + " 需要是布尔值 true/false。");
            }
        }

        return ToolCallValidationResult.ok();
    }

    private Optional<WechatToolDefinition> findDefinition(String toolName, List<WechatToolDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return Optional.empty();
        }
        return definitions.stream()
                .filter(definition -> definition != null && definition.name().equals(toolName))
                .findFirst();
    }

    private boolean isBooleanLike(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return List.of("true", "false", "yes", "no", "1", "0", "是", "否", "需要", "不需要")
                .contains(normalized);
    }
}
