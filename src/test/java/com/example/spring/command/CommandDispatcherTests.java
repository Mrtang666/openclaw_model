package com.example.spring.command;

import com.example.spring.exception.CommandException;
import com.example.spring.exception.WeatherServiceException;
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

    @Test
    void formatsCommandExceptionAsInputProblem() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "weather",
                new CommandException("缺少城市名，用法：weather <城市名>")));

        assertThat(dispatcher.dispatch("weather"))
                .isEqualTo("输入有问题：缺少城市名，用法：weather <城市名>");
    }

    @Test
    void formatsWeatherExceptionAsWeatherServiceProblem() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "weather",
                new WeatherServiceException("高德天气服务暂时不可用")));

        assertThat(dispatcher.dispatch("weather 北京"))
                .isEqualTo("天气服务异常：高德天气服务暂时不可用");
    }

    @Test
    void formatsUnexpectedExceptionWithRootCauseMessage() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "boom",
                new IllegalStateException("外层异常", new IllegalArgumentException("真实原因"))));

        assertThat(dispatcher.dispatch("boom"))
                .isEqualTo("系统异常：真实原因");
    }

    @Test
    void formatsUnexpectedExceptionWithoutMessage() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "boom",
                new IllegalStateException()));

        assertThat(dispatcher.dispatch("boom"))
                .isEqualTo("系统异常：程序执行失败，请稍后重试");
    }

    private CommandDispatcher dispatcherWith(Command command) {
        return new CommandDispatcher(new CommandRegistry(List.of(command)));
    }

    private Command commandThatThrows(String name, RuntimeException exception) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "异常测试命令";
            }

            @Override
            public String execute(List<String> arguments) {
                throw exception;
            }
        };
    }
}
