package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import java.util.List;

public record WechatToolDefinition(
        String name,
        String description,
        List<WechatToolParameter> parameters,
        WechatToolCapability capability) {

    public WechatToolDefinition(String name, String description, List<WechatToolParameter> parameters) {
        this(name, description, parameters, WechatToolCapability.empty());
    }

    public WechatToolDefinition {
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        capability = capability == null ? WechatToolCapability.empty() : capability;
    }

    public List<String> arguments() {
        return parameters.stream()
                .map(WechatToolParameter::name)
                .toList();
    }
}

