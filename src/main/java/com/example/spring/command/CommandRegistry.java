package com.example.spring.command;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public CommandRegistry(List<Command> commands) {
        commands.stream()
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .forEach(command -> this.commands.put(command.name(), command));
    }

    public Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }

    public Collection<Command> findAll() {
        return commands.values();
    }
}

