package com.example.spring.command;

import org.springframework.stereotype.Component;

import java.util.List;

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

