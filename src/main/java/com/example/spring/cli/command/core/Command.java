package com.example.spring.cli.command.core;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import java.util.List;

public interface Command {

    String name();

    String description();

    String execute(List<String> arguments);
}


