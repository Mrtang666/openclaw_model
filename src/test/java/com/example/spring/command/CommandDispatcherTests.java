package com.example.spring.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDispatcherTests {

    @Test
    void dispatchesRegisteredCommand() {
        Command command = new Command() {
            @Override
            public String name() {
                return "hello";
            }

            @Override
            public String description() {
                return "测试命令";
            }

            @Override
            public String execute(List<String> arguments) {
                return "hello " + String.join(" ", arguments);
            }
        };
        CommandDispatcher dispatcher = new CommandDispatcher(new CommandRegistry(List.of(command)));

        assertThat(dispatcher.dispatch("hello Codex")).isEqualTo("hello Codex");
    }

    @Test
    void returnsFriendlyMessageForUnknownCommand() {
        CommandDispatcher dispatcher = new CommandDispatcher(new CommandRegistry(List.of()));

        assertThat(dispatcher.dispatch("missing"))
                .isEqualTo("你好，我是你的AI助手");
    }
}
