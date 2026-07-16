package com.example.spring.command;

import com.example.spring.exception.CommandException;
import com.example.spring.exception.WeatherServiceException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandDispatcher {

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
            Command command = registry.find(commandName)
                    .orElseThrow(() -> new CommandException("未知命令：" + commandName));
            return command.execute(arguments);
        } catch (CommandException | WeatherServiceException exception) {
            return "错误：" + exception.getMessage();
        } catch (Exception exception) {
            return "错误：程序执行失败";
        }
    }
}

