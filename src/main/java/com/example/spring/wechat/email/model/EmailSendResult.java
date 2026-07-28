package com.example.spring.wechat.email.model;

import java.util.List;

public record EmailSendResult(
        List<String> to,
        String subject,
        String attachmentName,
        long attachmentSizeBytes) {
}
