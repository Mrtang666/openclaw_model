package com.openilink.exception;

public class ILinkException extends RuntimeException {
    public ILinkException() { super(); }
    public ILinkException(String message) { super(message); }
    public ILinkException(String message, Throwable cause) { super(message, cause); }
}
