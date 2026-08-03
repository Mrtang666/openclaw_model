package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.exception.CommandException;
import com.example.spring.wechat.bot.WechatReply;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WechatToolRegistry {

    private final Map<String, WechatTool> tools;

    public WechatToolRegistry(List<WechatTool> tools) {
        if (tools == null || tools.isEmpty()) {
            this.tools = Map.of();
            return;
        }
        Map<String, WechatTool> registered = new LinkedHashMap<>();
        for (WechatTool tool : tools) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                throw new IllegalArgumentException("微信工具名称不能为空");
            }
            String name = tool.name().strip();
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalArgumentException("微信工具名称重复：" + name);
            }
        }
        this.tools = Map.copyOf(registered);
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
                .map(tool -> new WechatToolDefinition(tool.name(), tool.description(), tool.parameters(), tool.capability()))
                .sorted(java.util.Comparator.comparing(WechatToolDefinition::name))
                .toList();
    }
}

