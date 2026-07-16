package com.openilink.exception;

public class NoContextTokenException extends ILinkException {
    public NoContextTokenException(String userId) {
        super("No context token for user: " + userId);
    }
}
