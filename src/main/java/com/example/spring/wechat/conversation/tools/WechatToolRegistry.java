package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.exception.CommandException;
import com.example.spring.wechat.bot.WechatReply;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WechatToolRegistry {

    private final Map<String, WechatTool> tools;

    public WechatToolRegistry(List<WechatTool> tools) {
        this.tools = tools == null ? Map.of() : tools.stream()
                .collect(Collectors.toUnmodifiableMap(WechatTool::name, Function.identity()));
    }

    public WechatReply execute(String name, WechatToolRequest request) {
        WechatTool tool = tools.get(name);
        if (tool == null) {
            throw new CommandException("微信工具不存在：" + name);
        }
        return tool.execute(request);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<String> names() {
        return tools.keySet().stream().sorted().toList();
    }

    public List<WechatToolDefinition> definitions() {
        return tools.values().stream()
                .map(tool -> new WechatToolDefinition(tool.name(), tool.description(), tool.arguments()))
                .sorted(java.util.Comparator.comparing(WechatToolDefinition::name))
                .toList();
    }
}

