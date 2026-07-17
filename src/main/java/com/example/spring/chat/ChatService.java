package com.example.spring.chat;

import com.example.spring.agent.ReplyEmitter;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String reply(String userMessage) {
        StringBuilder output = new StringBuilder();
        streamReply(userMessage, output::append);
        return output.toString().strip();
    }

    public void streamReply(String userMessage, ReplyEmitter emitter) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new ChatServiceException("缺少对话内容");
        }

        if (emitter == null) {
            throw new ChatServiceException("缺少流式输出处理器");
        }

        StringBuilder collected = new StringBuilder();
        chatClient.streamReply(userMessage.strip(), chunk -> {
            if (chunk != null) {
                collected.append(chunk);
                emitter.emit(chunk);
            }
        });

        if (collected.toString().isBlank()) {
            throw new ChatServiceException("大模型未返回有效回复");
        }
    }
}
