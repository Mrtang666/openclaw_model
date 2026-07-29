package com.example.spring.wechat.email.model;

import java.util.List;

public record EmailSendResult(
        Status status,
        String userMessage,
        String confirmToken,
        List<String> to,
        String subject,
        String attachmentName,
        long attachmentSizeBytes) {

    public EmailSendResult {
        status = status == null ? Status.FAILED : status;
        userMessage = userMessage == null ? "" : userMessage.strip();
        confirmToken = confirmToken == null ? "" : confirmToken.strip();
        to = to == null ? List.of() : List.copyOf(to);
        subject = subject == null ? "" : subject.strip();
        attachmentName = attachmentName == null ? "" : attachmentName.strip();
        attachmentSizeBytes = Math.max(0, attachmentSizeBytes);
    }

    public EmailSendResult(List<String> to, String subject, String attachmentName, long attachmentSizeBytes) {
        this(Status.SENT, "", "", to, subject, attachmentName, attachmentSizeBytes);
    }

    public static EmailSendResult sent(String message) {
        return new EmailSendResult(Status.SENT, message, "", List.of(), "", "", 0);
    }

    public static EmailSendResult pending(String message, String confirmToken) {
        return new EmailSendResult(Status.PENDING_CONFIRMATION, message, confirmToken, List.of(), "", "", 0);
    }

    public static EmailSendResult needsInput(String message) {
        return new EmailSendResult(Status.NEEDS_INPUT, message, "", List.of(), "", "", 0);
    }

    public static EmailSendResult failed(String message) {
        return new EmailSendResult(Status.FAILED, message, "", List.of(), "", "", 0);
    }

    public enum Status {
        SENT,
        PENDING_CONFIRMATION,
        NEEDS_INPUT,
        FAILED
    }
}
