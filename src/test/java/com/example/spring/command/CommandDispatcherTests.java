package com.example.spring.command;

import com.example.spring.chat.ChatService;
import com.example.spring.exception.CommandException;
import com.example.spring.exception.WeatherServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        assertThat(dispatcher.dispatch("/hello Codex")).isEqualTo("hello Codex");
    }

    @Test
    void dispatchesNormalTextToChatModel() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply("你是谁")).thenReturn("我是你的 AI 助手");
        CommandDispatcher dispatcher = new CommandDispatcher(new CommandRegistry(List.of()), chatService);

        assertThat(dispatcher.dispatch("你是谁"))
                .isEqualTo("我是你的 AI 助手");
        verify(chatService).reply("你是谁");
    }

    @Test
    void streamsNormalTextFromChatModel() throws Exception {
        ChatService chatService = mock(ChatService.class);
        CommandDispatcher dispatcher = new CommandDispatcher(new CommandRegistry(List.of()), chatService);
        StringBuilder output = new StringBuilder();
        org.mockito.Mockito.doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("你好");
            emitter.emit("，我是 AI");
            return null;
        }).when(chatService).streamReply(org.mockito.Mockito.eq("你是谁"), org.mockito.Mockito.any());

        dispatcher.dispatchStreaming("你是谁", output::append);

        assertThat(output).hasToString("你好，我是 AI");
    }

    @Test
    void formatsCommandExceptionAsInputProblem() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "weather",
                new CommandException("缺少城市名，用法：weather <城市名>")));

        assertThat(dispatcher.dispatch("/weather"))
                .isEqualTo("输入有问题：缺少城市名，用法：weather <城市名>");
    }

    @Test
    void formatsWeatherExceptionAsWeatherServiceProblem() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "weather",
                new WeatherServiceException("高德天气服务暂时不可用")));

        assertThat(dispatcher.dispatch("/weather 北京"))
                .isEqualTo("天气服务异常：高德天气服务暂时不可用");
    }

    @Test
    void formatsUnexpectedExceptionWithRootCauseMessage() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "boom",
                new IllegalStateException("外层异常", new IllegalArgumentException("真实原因"))));

        assertThat(dispatcher.dispatch("/boom"))
                .isEqualTo("系统异常：真实原因");
    }

    @Test
    void formatsUnexpectedExceptionWithoutMessage() {
        CommandDispatcher dispatcher = dispatcherWith(commandThatThrows(
                "boom",
                new IllegalStateException()));

        assertThat(dispatcher.dispatch("/boom"))
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
