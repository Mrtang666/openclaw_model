package com.example.spring.wechat.email.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class EmailMailConfiguration {

    @Bean
    public JavaMailSender emailJavaMailSender(EmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.smtp().host());
        sender.setPort(properties.smtp().port());
        sender.setUsername(properties.smtp().username());
        sender.setPassword(properties.smtp().password());

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        javaMailProperties.put("mail.smtp.auth", "true");
        javaMailProperties.put("mail.smtp.ssl.enable", String.valueOf(properties.smtp().sslEnabled()));
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(!properties.smtp().sslEnabled()));
        javaMailProperties.put("mail.smtp.connectiontimeout", String.valueOf(properties.smtp().timeoutMs()));
        javaMailProperties.put("mail.smtp.timeout", String.valueOf(properties.smtp().timeoutMs()));
        javaMailProperties.put("mail.smtp.writetimeout", String.valueOf(properties.smtp().timeoutMs()));
        return sender;
    }
}
