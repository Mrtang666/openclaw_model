package com.example.spring.wechat.email.model;

import java.time.Instant;
import java.util.List;

public record EmailMessageDetail(
        String uid,
        String from,
        List<String> to,
        String subject,
        Instant sentAt,
        boolean unread,
        int attachmentCount,
        String text) {
}
