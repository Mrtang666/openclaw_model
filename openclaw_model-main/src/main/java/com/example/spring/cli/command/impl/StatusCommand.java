package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import org.springframework.stereotype.Component;

import java.util.List;
import com.example.spring.cli.command.core.Command;

@Component
public class StatusCommand implements Command {

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "显示程序状态";
    }

    @Override
    public String execute(List<String> arguments) {
        return "状态：RUNNING";
    }
}


