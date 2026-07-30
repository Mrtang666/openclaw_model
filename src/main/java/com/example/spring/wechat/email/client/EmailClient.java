package com.example.spring.wechat.email.client;

import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailAttachment;

import java.util.List;

public interface EmailClient {
    void send(EmailMessage message);

    default void sendWithAttachments(EmailMessage message, List<EmailAttachment> attachments) {
        throw new UnsupportedOperationException("Email attachments are not supported by this client");
    }
}
