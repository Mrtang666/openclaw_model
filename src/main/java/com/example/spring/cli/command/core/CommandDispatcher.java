package com.example.spring.cli.command.core;


/**
 * CLI 命令系统组件，负责解析和执行本地命令。
 */
import com.example.spring.agent.ReplyEmitter;
import com.example.spring.chat.ChatService;
import com.example.spring.chat.ChatServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandDispatcher {

    private static final String DEFAULT_ASSISTANT_REPLY = "你好，我是你的AI助手";

    private final CommandRegistry registry;
    private final ChatService chatService;

    @Autowired
    public CommandDispatcher(CommandRegistry registry, ChatService chatService) {
        this.registry = registry;
        this.chatService = chatService;
    }

    public CommandDispatcher(CommandRegistry registry) {
        this(registry, null);
    }

    public String dispatch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String text = input.strip();
        if (!text.startsWith("/") && chatService != null) {
            try {
                return chatService.reply(text);
            } catch (Exception exception) {
                return ErrorMessageFormatter.format(exception);
            }
        }

        StringBuilder output = new StringBuilder();
        dispatchStreaming(input, output::append);
        return output.toString();
    }

    public void dispatchStreaming(String input, ReplyEmitter emitter) {
        if (input == null || input.isBlank()) {
            return;
        }

        if (emitter == null) {
            throw new ChatServiceException("缺少流式输出处理器");
        }

        String text = input.strip();
        if (!text.startsWith("/")) {
            dispatchChat(text, emitter);
            return;
        }

        String commandLine = text.substring(1).strip();
        if (commandLine.isBlank()) {
            emit(emitter, "输入有问题：缺少命令名，用法：/<命令> [参数]");
            return;
        }

        String[] parts = commandLine.split("\\s+");
        String commandName = parts[0].toLowerCase();
        List<String> arguments = Arrays.asList(parts).subList(1, parts.length);

        Command command = registry.find(commandName).orElse(null);
        if (command == null) {
            emit(emitter, "输入有问题：未知命令：" + commandName + "，输入 /help 查看可用命令");
            return;
        }

        String output;
        try {
            output = command.execute(arguments);
        } catch (Exception exception) {
            output = ErrorMessageFormatter.format(exception);
        }
        emit(emitter, output);
    }

    private void dispatchChat(String text, ReplyEmitter emitter) {
        if (chatService == null) {
            emit(emitter, DEFAULT_ASSISTANT_REPLY);
            return;
        }

        try {
            chatService.streamReply(text, emitter);
        } catch (ChatServiceException exception) {
            emit(emitter, ErrorMessageFormatter.format(exception));
        }
    }

    private void emit(ReplyEmitter emitter, String text) {
        if (text != null && !text.isBlank()) {
            emitter.emit(text);
        }
    }
}

