package com.example.spring.chat;


/**
 * 文本大模型接入层组件，负责封装模型请求、响应和异常。
 */
import com.example.spring.agent.ReplyEmitter;

public interface ChatClient {

    ChatReply reply(String userMessage);

    void streamReply(String userMessage, ReplyEmitter emitter);
}

