package com.example.spring.wechat.email.client;

import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailAttachment;
import com.example.spring.wechat.email.model.EmailMessage;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailClientTests {

    @Test
    void mapsEmailMessageToSimpleMailMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailClient client = new SmtpEmailClient(sender, properties());
        EmailMessage message = new EmailMessage(
                List.of("to@example.com"),
                "Subject",
                "Body",
                List.of("cc@example.com"),
                List.of("bcc@example.com"));

        client.send(message);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("sender@qq.com");
        assertThat(sent.getTo()).containsExactly("to@example.com");
        assertThat(sent.getCc()).containsExactly("cc@example.com");
        assertThat(sent.getBcc()).containsExactly("bcc@example.com");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getText()).isEqualTo("Body");
    }

    @Test
    void wrapsSpringMailException() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("boom")).when(sender).send(any(SimpleMailMessage.class));
        SmtpEmailClient client = new SmtpEmailClient(sender, properties());

        assertThatThrownBy(() -> client.send(new EmailMessage(List.of("to@example.com"), "Subject", "Body", List.of(), List.of())))
                .isInstanceOf(EmailClientException.class)
                .hasMessageContaining("SMTP");
    }

    @Test
    void sendsMimeMessageWithAttachment() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpEmailClient client = new SmtpEmailClient(sender, properties());

        client.sendWithAttachments(
                new EmailMessage(List.of("to@example.com"), "Report", "See attachment", List.of(), List.of()),
                List.of(new EmailAttachment("xlsx".getBytes(), "report.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));

        verify(sender).send(mimeMessage);
        mimeMessage.saveChanges();
        assertThat(mimeMessage.getSubject()).isEqualTo("Report");
        assertThat(mimeMessage.getContent()).isInstanceOf(Multipart.class);
        Multipart content = (Multipart) mimeMessage.getContent();
        assertThat(content.getCount()).isEqualTo(2);
        assertThat(content.getBodyPart(1).getFileName()).isEqualTo("report.xlsx");
    }

    private static EmailProperties properties() {
        return new EmailProperties(
                true,
                "qq",
                new EmailProperties.Smtp("smtp.qq.com", 465, true, "sender@qq.com", "auth-code", "sender@qq.com", 15_000),
                "to@example.com",
                true,
                10,
                8_000);
    }
}
