package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import com.example.spring.exception.CommandException;
import com.example.spring.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import com.example.spring.cli.command.core.Command;

@Component
public class WeatherCommand implements Command {

    private final ToolRegistry tools;

    public WeatherCommand(ToolRegistry tools) {
        this.tools = tools;
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询天气，用法：/weather <城市名>";
    }

    @Override
    public String execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            throw new CommandException("缺少城市名，用法：weather <城市名>");
        }

        String city = String.join(" ", arguments);
        return tools.execute("weather", Map.of("city", city));
    }
}

