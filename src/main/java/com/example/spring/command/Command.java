package com.example.spring.command;

import java.util.List;

public interface Command {

    String name();

    String description();

    String execute(List<String> arguments);
}

