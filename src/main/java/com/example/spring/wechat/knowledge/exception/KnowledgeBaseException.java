package com.example.spring.wechat.knowledge.exception;

/**
 * 知识库相关异常。
 */
public class KnowledgeBaseException extends RuntimeException {

    public KnowledgeBaseException(String message) {
        super(message);
    }

    public KnowledgeBaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
