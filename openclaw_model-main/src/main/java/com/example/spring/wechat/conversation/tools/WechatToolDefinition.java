package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import java.util.List;

public record WechatToolDefinition(String name, String description, List<String> arguments) {

    public WechatToolDefinition {
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}

