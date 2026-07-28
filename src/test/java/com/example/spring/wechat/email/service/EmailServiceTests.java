package com.example.spring.wechat.email.service;

import com.example.spring.wechat.email.client.EmailClient;
import com.example.spring.wechat.email.client.EmailClientException;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailSendResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailServiceTests {

    @Test
    void refusesWhenDisabled() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(properties(false, "friend@example.com"), client, clock(), () -> "token-1");

        EmailSendResult result = service.sendOrStage("session-1", message("friend@example.com"), "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(result.userMessage()).contains("邮箱功能还没有启用");
        assertThat(client.sentMessages).isEmpty();
    }

    @Test
    void asksForMissingRequiredFields() {
        EmailService service = service(properties(true, "friend@example.com"), new RecordingEmailClient(), clock(), () -> "token-1");

        EmailSendResult result = service.sendOrStage(
                "session-1",
                new EmailMessage(List.of(), "", "", List.of(), List.of()),
                "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.NEEDS_INPUT);
        assertThat(result.userMessage()).contains("收件人").contains("主题").contains("正文");
    }

    @Test
    void rejectsInvalidAddressBeforeSending() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(properties(true, "friend@example.com"), client, clock(), () -> "token-1");

        EmailSendResult result = service.sendOrStage("session-1", message("bad-address"), "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(result.userMessage()).contains("邮箱地址格式不正确").contains("bad-address");
        assertThat(client.sentMessages).isEmpty();
    }

    @Test
    void sendsImmediatelyWhenAllRecipientsAreWhitelisted() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(
                properties(true, "friend@example.com,copy@example.com,hidden@example.com"),
                client,
                clock(),
                () -> "token-1");
        EmailMessage message = new EmailMessage(
                List.of("friend@example.com"),
                "Hello",
                "Body",
                List.of("copy@example.com"),
                List.of("hidden@example.com"));

        EmailSendResult result = service.sendOrStage("session-1", message, "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.SENT);
        assertThat(result.userMessage()).contains("邮件已发送").contains("Hello").contains("f***@example.com");
        assertThat(client.sentMessages).containsExactly(message);
    }

    @Test
    void createsPendingDraftForNonWhitelistedRecipient() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(properties(true, "friend@example.com"), client, clock(), () -> "token-1");

        EmailSendResult result = service.sendOrStage("session-1", message("other@example.com"), "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.PENDING_CONFIRMATION);
        assertThat(result.confirmToken()).isEqualTo("token-1");
        assertThat(result.userMessage())
                .contains("需要确认")
                .contains("other@example.com")
                .contains("主题")
                .contains("token-1");
        assertThat(client.sentMessages).isEmpty();
    }

    @Test
    void sendsPendingDraftWithValidConfirmationToken() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(properties(true, "friend@example.com"), client, clock(), () -> "token-1");
        EmailMessage draft = message("other@example.com");
        service.sendOrStage("session-1", draft, "");

        EmailSendResult result = service.sendOrStage("session-1", new EmailMessage(List.of(), "", "", List.of(), List.of()), "token-1");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.SENT);
        assertThat(client.sentMessages).containsExactly(draft);

        EmailSendResult reused = service.sendOrStage("session-1", new EmailMessage(List.of(), "", "", List.of(), List.of()), "token-1");
        assertThat(reused.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(reused.userMessage()).contains("确认令牌无效");
    }

    @Test
    void rejectsConfirmationTokenFromAnotherSession() {
        RecordingEmailClient client = new RecordingEmailClient();
        EmailService service = service(properties(true, "friend@example.com"), client, clock(), () -> "token-1");
        service.sendOrStage("session-1", message("other@example.com"), "");

        EmailSendResult result = service.sendOrStage("session-2", new EmailMessage(List.of(), "", "", List.of(), List.of()), "token-1");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(result.userMessage()).contains("确认令牌无效");
        assertThat(client.sentMessages).isEmpty();
    }

    @Test
    void rejectsExpiredConfirmationToken() {
        RecordingEmailClient client = new RecordingEmailClient();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        EmailService service = service(properties(true, "friend@example.com"), client, clock, () -> "token-1");
        service.sendOrStage("session-1", message("other@example.com"), "");
        clock.instant = Instant.parse("2026-07-28T00:11:00Z");

        EmailSendResult result = service.sendOrStage("session-1", new EmailMessage(List.of(), "", "", List.of(), List.of()), "token-1");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(result.userMessage()).contains("确认令牌已过期");
        assertThat(client.sentMessages).isEmpty();
    }

    @Test
    void mapsClientExceptionToSafeFailure() {
        RecordingEmailClient client = new RecordingEmailClient();
        client.failure = new EmailClientException("auth failed");
        EmailService service = service(properties(true, "friend@example.com"), client, clock(), () -> "token-1");

        EmailSendResult result = service.sendOrStage("session-1", message("friend@example.com"), "");

        assertThat(result.status()).isEqualTo(EmailSendResult.Status.FAILED);
        assertThat(result.userMessage()).contains("邮件发送失败").contains("QQ 邮箱授权码");
    }

    private static EmailMessage message(String to) {
        return new EmailMessage(List.of(to), "主题", "正文内容", List.of(), List.of());
    }

    private static EmailProperties properties(boolean enabled, String allowedRecipients) {
        return new EmailProperties(
                enabled,
                "qq",
                new EmailProperties.Smtp("smtp.qq.com", 465, true, "sender@qq.com", "auth-code", "sender@qq.com", 15_000),
                allowedRecipients,
                true,
                10,
                8_000);
    }

    private static EmailService service(
            EmailProperties properties,
            RecordingEmailClient client,
            Clock clock,
            java.util.function.Supplier<String> tokenSupplier) {
        return new EmailService(properties, new PendingEmailDraftService(clock, tokenSupplier), client);
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneId.of("UTC"));
    }

    private static class RecordingEmailClient implements EmailClient {
        private final List<EmailMessage> sentMessages = new ArrayList<>();
        private EmailClientException failure;

        @Override
        public void send(EmailMessage message) {
            if (failure != null) {
                throw failure;
            }
            sentMessages.add(message);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
