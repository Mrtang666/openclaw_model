package com.example.spring.wechat.email.model;

public record EmailSendResult(
        Status status,
        String userMessage,
        String confirmToken) {

    public EmailSendResult {
        status = status == null ? Status.FAILED : status;
        userMessage = userMessage == null ? "" : userMessage.strip();
        confirmToken = confirmToken == null ? "" : confirmToken.strip();
    }

    public static EmailSendResult sent(String message) {
        return new EmailSendResult(Status.SENT, message, "");
    }

    public static EmailSendResult pending(String message, String confirmToken) {
        return new EmailSendResult(Status.PENDING_CONFIRMATION, message, confirmToken);
    }

    public static EmailSendResult needsInput(String message) {
        return new EmailSendResult(Status.NEEDS_INPUT, message, "");
    }

    public static EmailSendResult failed(String message) {
        return new EmailSendResult(Status.FAILED, message, "");
    }

    public enum Status {
        SENT,
        PENDING_CONFIRMATION,
        NEEDS_INPUT,
        FAILED
    }
}
