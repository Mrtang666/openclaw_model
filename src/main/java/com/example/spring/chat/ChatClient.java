package com.example.spring.chat;

import com.example.spring.agent.ReplyEmitter;

public interface ChatClient {

    ChatReply reply(String userMessage);

    void streamReply(String userMessage, ReplyEmitter emitter);
}
