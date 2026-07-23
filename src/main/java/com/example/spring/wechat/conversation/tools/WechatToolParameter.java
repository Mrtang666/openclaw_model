package com.example.spring.wechat.conversation.tools;

import java.util.List;

/**
 * 微信工具参数定义。
 * 用它描述参数名、类型、是否必填、参数说明、可选值和示例值。
 */
public record WechatToolParameter(
        String name,
        String type,
        boolean required,
        String description,
        List<String> allowedValues,
        String example) {

    public WechatToolParameter {
        name = name == null ? "" : name.strip();
        type = type == null || type.isBlank() ? "string" : type.strip();
        description = description == null ? "" : description.strip();
        allowedValues = allowedValues == null
                ? List.of()
                : allowedValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
        example = example == null ? "" : example.strip();
    }

    public static WechatToolParameter optionalString(String name, String description, String example) {
        return new WechatToolParameter(name, "string", false, description, List.of(), example);
    }

    public static WechatToolParameter requiredString(String name, String description, String example) {
        return new WechatToolParameter(name, "string", true, description, List.of(), example);
    }

    public static WechatToolParameter optionalBoolean(String name, String description, boolean example) {
        return new WechatToolParameter(name, "boolean", false, description, List.of("true", "false"), String.valueOf(example));
    }

    public static WechatToolParameter optionalStringArray(String name, String description, String example) {
        return new WechatToolParameter(name, "array", false, description, List.of(), example);
    }

    public static WechatToolParameter optionalEnum(
            String name,
            String description,
            List<String> allowedValues,
            String example) {
        return new WechatToolParameter(name, "string", false, description, allowedValues, example);
    }
}
