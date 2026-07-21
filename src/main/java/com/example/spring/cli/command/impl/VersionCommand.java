package com.example.spring.cli.command.impl;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import com.example.spring.cli.command.core.Command;

@Component
public class VersionCommand implements Command {

    private final String version;

    public VersionCommand(@Value("${app.version:dev}") String version) {
        this.version = version;
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String description() {
        return "显示版本";
    }

    @Override
    public String execute(List<String> arguments) {
        return "版本：" + version;
    }
}


