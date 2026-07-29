package com.example.spring.wechat.email.model;

import java.util.List;

public record EmailMessage(
        List<String> to,
        String subject,
        String body,
        List<String> cc,
        List<String> bcc) {

    public EmailMessage {
        to = cleanList(to);
        subject = safe(subject);
        body = body == null ? "" : body.strip();
        cc = cleanList(cc);
        bcc = cleanList(bcc);
    }

    public List<String> allRecipients() {
        java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
        recipients.addAll(to);
        recipients.addAll(cc);
        recipients.addAll(bcc);
        return List.copyOf(recipients);
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
