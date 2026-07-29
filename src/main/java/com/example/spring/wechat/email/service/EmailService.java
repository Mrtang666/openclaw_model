package com.example.spring.wechat.email.service;

import com.example.spring.wechat.email.client.EmailClient;
import com.example.spring.wechat.email.client.EmailClientException;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.wechat.email.model.EmailSendResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailProperties properties;
    private final PendingEmailDraftService pendingEmailDraftService;
    private final EmailClient emailClient;

    public EmailService(
            EmailProperties properties,
            PendingEmailDraftService pendingEmailDraftService,
            EmailClient emailClient) {
        this.properties = properties;
        this.pendingEmailDraftService = pendingEmailDraftService;
        this.emailClient = emailClient;
    }

    public EmailSendResult sendOrStage(String sessionKey, EmailMessage message, String confirmToken) {
        EmailSendResult configResult = validateEnabledAndConfigured();
        if (configResult != null) {
            return configResult;
        }

        if (confirmToken != null && !confirmToken.isBlank()) {
            return sendPendingDraft(sessionKey, confirmToken);
        }

        EmailSendResult validationResult = validateMessage(message);
        if (validationResult != null) {
            return validationResult;
        }

        if (requiresConfirmation(message)) {
            PendingEmailDraftService.PendingEmailDraft draft = pendingEmailDraftService.create(
                    sessionKey, message, properties.pendingDraftTtlMinutes());
            return EmailSendResult.pending(pendingMessage(message, draft.token()), draft.token());
        }

        return send(message);
    }

    private EmailSendResult sendPendingDraft(String sessionKey, String confirmToken) {
        PendingEmailDraftService.DraftLookupResult result = pendingEmailDraftService.consume(sessionKey, confirmToken);
        return switch (result.status()) {
            case FOUND -> send(result.draft().orElseThrow().message());
            case EXPIRED -> EmailSendResult.failed("确认令牌已过期，请重新创建邮件草稿。");
            case WRONG_SESSION, NOT_FOUND -> EmailSendResult.failed("确认令牌无效，请重新创建邮件草稿。");
        };
    }

    private EmailSendResult send(EmailMessage message) {
        try {
            emailClient.send(message);
            return EmailSendResult.sent("邮件已发送：%s\n收件人：%s".formatted(
                    message.subject(), maskedRecipients(message.allRecipients())));
        } catch (EmailClientException exception) {
            return EmailSendResult.failed("邮件发送失败，请检查 QQ 邮箱授权码或稍后重试。");
        } catch (RuntimeException exception) {
            return EmailSendResult.failed("邮件发送失败，服务暂时不可用，请稍后重试。");
        }
    }

    private EmailSendResult validateEnabledAndConfigured() {
        if (!properties.enabled()) {
            return EmailSendResult.failed("邮箱功能还没有启用。");
        }
        if (properties.smtp().username().isBlank()
                || properties.smtp().password().isBlank()
                || properties.fromAddress().isBlank()) {
            return EmailSendResult.failed("邮箱 SMTP 配置不完整，请检查发件邮箱和 QQ 邮箱授权码。");
        }
        return null;
    }

    private EmailSendResult validateMessage(EmailMessage message) {
        if (message == null) {
            return EmailSendResult.needsInput("请提供收件人、主题和正文。");
        }

        List<String> missing = new ArrayList<>();
        if (message.to().isEmpty()) {
            missing.add("收件人");
        }
        if (message.subject().isBlank()) {
            missing.add("主题");
        }
        if (message.body().isBlank()) {
            missing.add("正文");
        }
        if (!missing.isEmpty()) {
            return EmailSendResult.needsInput("请补充邮件" + String.join("、", missing) + "。");
        }
        if (message.body().length() > properties.maxBodyChars()) {
            return EmailSendResult.failed("邮件正文太长，请控制在 " + properties.maxBodyChars() + " 字以内。");
        }
        for (String recipient : message.allRecipients()) {
            if (!EMAIL_PATTERN.matcher(recipient).matches()) {
                return EmailSendResult.failed("邮箱地址格式不正确：" + recipient);
            }
        }
        return null;
    }

    private boolean requiresConfirmation(EmailMessage message) {
        if (!properties.requireConfirmationForNonWhitelist()) {
            return false;
        }
        return message.allRecipients().stream()
                .anyMatch(recipient -> !properties.isAllowedRecipient(recipient));
    }

    private String pendingMessage(EmailMessage message, String token) {
        return """
                这封邮件包含非白名单收件人，需要确认后发送。
                收件人：%s
                主题：%s
                正文预览：%s
                确认令牌：%s
                """.formatted(
                String.join(", ", message.allRecipients()),
                message.subject(),
                preview(message.body()),
                token).strip();
    }

    private String preview(String body) {
        String normalized = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }

    private String maskedRecipients(List<String> recipients) {
        return recipients.stream()
                .map(this::maskEmail)
                .toList()
                .toString();
    }

    private String maskEmail(String address) {
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = address.substring(0, at);
        String domain = address.substring(at);
        String prefix = local.substring(0, 1);
        return prefix + "***" + domain;
    }
}
