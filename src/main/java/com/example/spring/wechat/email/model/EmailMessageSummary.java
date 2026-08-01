package com.example.spring.wechat.email.model;

import java.time.Instant;

public record EmailMessageSummary(
        String uid,
        String from,
        String subject,
        Instant sentAt,
        boolean unread,
        boolean hasAttachments,
        int attachmentCount,
        String preview) {
}
