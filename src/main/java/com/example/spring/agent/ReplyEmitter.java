package com.example.spring.agent;

@FunctionalInterface
public interface ReplyEmitter {

    void emit(String text);
}
