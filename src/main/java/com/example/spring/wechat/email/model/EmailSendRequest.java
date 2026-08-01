package com.example.spring.wechat.email.model;

import java.nio.file.Path;
import java.util.List;

public record EmailSendRequest(
        List<String> to,
        String subject,
        String body,
        Path attachmentPath) {

    public EmailSendRequest {
        to = to == null
                ? List.of()
                : to.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        subject = subject == null || subject.isBlank() ? "OpenClaw 文件发送" : subject.strip();
        body = body == null ? "" : body.strip();
    }
}
