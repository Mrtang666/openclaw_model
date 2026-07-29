package com.example.spring.wechat.email.client;

import com.example.spring.wechat.email.model.EmailMessage;

public interface EmailClient {
    void send(EmailMessage message);
}
