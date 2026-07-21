package com.example.spring.agent;


/**
 * Agent 入口层组件，负责协调 CLI 输入和回复输出。
 */
@FunctionalInterface
public interface ReplyEmitter {

    void emit(String text);
}

