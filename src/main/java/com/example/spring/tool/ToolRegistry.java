package com.example.spring.tool;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.exception.CommandException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    public String execute(String name, Map<String, String> arguments) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new CommandException("工具不存在：" + name);
        }
        return tool.execute(arguments);
    }
}


