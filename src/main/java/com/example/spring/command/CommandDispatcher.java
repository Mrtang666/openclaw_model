package com.example.spring.command;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandDispatcher {

    private static final String DEFAULT_ASSISTANT_REPLY = "你好，我是你的AI助手";

    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public String dispatch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toLowerCase();
        List<String> arguments = Arrays.asList(parts).subList(1, parts.length);

        try {
            Command command = registry.find(commandName).orElse(null);
            if (command == null) {
                return DEFAULT_ASSISTANT_REPLY;
            }
            return command.execute(arguments);
        } catch (Exception exception) {
            return ErrorMessageFormatter.format(exception);
        }
    }
}
