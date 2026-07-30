package com.example.spring.wechat.email.client;

import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailAttachment;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Override
    public void sendWithAttachments(EmailMessage message, List<EmailAttachment> attachments) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress());
            helper.setTo(message.to().toArray(String[]::new));
            if (!message.cc().isEmpty()) {
                helper.setCc(message.cc().toArray(String[]::new));
            }
            if (!message.bcc().isEmpty()) {
                helper.setBcc(message.bcc().toArray(String[]::new));
            }
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            if (attachments != null) {
                for (EmailAttachment attachment : attachments) {
                    if (attachment != null && attachment.bytes().length > 0) {
                        helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.bytes()),
                                attachment.contentType());
                    }
                }
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException exception) {
            throw new EmailClientException("SMTP email attachment send failed", exception);
        }
    }
}
