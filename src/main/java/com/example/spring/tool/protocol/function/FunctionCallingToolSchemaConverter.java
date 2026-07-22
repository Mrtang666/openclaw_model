package com.example.spring.tool.protocol.function;

import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Calling 工具 schema 转换器。
 * 负责把项目内部的微信工具定义转换为 OpenAI-compatible tools 数组，后续可直接传给兼容接口。
 */
@Component
public class FunctionCallingToolSchemaConverter {

    public List<Map<String, Object>> convert(List<WechatToolDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (WechatToolDefinition definition : definitions) {
            if (definition == null || definition.name().isBlank()) {
                continue;
            }
            tools.add(toolSchema(definition));
        }
        return tools;
    }

    private Map<String, Object> toolSchema(WechatToolDefinition definition) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        function.put("description", descriptionWithCapability(definition));
        function.put("parameters", parametersSchema(definition.parameters()));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private String descriptionWithCapability(WechatToolDefinition definition) {
        String description = definition.description();
        String capability = definition.capability().toPromptText();
        if (capability.isBlank()) {
            return description;
        }
        if (description.isBlank()) {
            return capability;
        }
        return description + System.lineSeparator() + capability;
    }

    private Map<String, Object> parametersSchema(List<WechatToolParameter> parameters) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (parameters != null) {
            for (WechatToolParameter parameter : parameters) {
                if (parameter == null || parameter.name().isBlank()) {
                    continue;
                }
                properties.put(parameter.name(), parameterSchema(parameter));
                if (parameter.required()) {
                    required.add(parameter.name());
                }
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> parameterSchema(WechatToolParameter parameter) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", normalizeType(parameter.type()));
        if (!parameter.description().isBlank()) {
            schema.put("description", parameter.description());
        }
        if (!parameter.allowedValues().isEmpty()) {
            schema.put("enum", parameter.allowedValues());
        }
        if (!parameter.example().isBlank()) {
            schema.put("example", parameter.example());
        }
        return schema;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "string";
        }
        return switch (type.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "boolean", "integer", "number", "array", "object" -> type.strip().toLowerCase(java.util.Locale.ROOT);
            default -> "string";
        };
    }
}
