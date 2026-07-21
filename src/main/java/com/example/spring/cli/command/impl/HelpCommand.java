package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import com.example.spring.cli.command.core.Command;
import com.example.spring.cli.command.core.CommandRegistry;

@Component
public class HelpCommand implements Command {

    private final ObjectProvider<CommandRegistry> registryProvider;

    public HelpCommand(ObjectProvider<CommandRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "显示帮助信息";
    }

    @Override
    public String execute(List<String> arguments) {
        String commands = registryProvider.getObject().findAll().stream()
                .map(command -> "/" + command.name() + " - " + command.description())
                .collect(Collectors.joining(System.lineSeparator()));
        return "可用命令：" + System.lineSeparator()
                + commands + System.lineSeparator()
                + "直接输入普通文字 - 与大模型对话" + System.lineSeparator()
                + "exit - 退出程序";
    }
}

