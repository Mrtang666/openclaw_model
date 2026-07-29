package com.example.spring.wechat.email.model;

import java.util.List;

public record EmailUnreadBatchResult(
        int totalUnread,
        List<EmailMessageDetail> messages) {

    public EmailUnreadBatchResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public int remainingUnread() {
        return Math.max(0, totalUnread - messages.size());
    }
}
