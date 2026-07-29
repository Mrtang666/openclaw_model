package com.example.spring.wechat.email.client;

import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailClient implements EmailClient {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailClient(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(properties.fromAddress());
        mailMessage.setTo(message.to().toArray(String[]::new));
        if (!message.cc().isEmpty()) {
            mailMessage.setCc(message.cc().toArray(String[]::new));
        }
        if (!message.bcc().isEmpty()) {
            mailMessage.setBcc(message.bcc().toArray(String[]::new));
        }
        mailMessage.setSubject(message.subject());
        mailMessage.setText(message.body());
        try {
            mailSender.send(mailMessage);
        } catch (MailException exception) {
            throw new EmailClientException("SMTP email send failed", exception);
        }
    }
}
