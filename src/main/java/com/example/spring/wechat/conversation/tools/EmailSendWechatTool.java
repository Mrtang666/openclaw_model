package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.EmailToolException;
import com.example.spring.wechat.email.model.EmailSendRequest;
import com.example.spring.wechat.email.model.EmailSendResult;
import com.example.spring.wechat.email.model.PreparedEmailAttachment;
import com.example.spring.wechat.email.service.EmailSendService;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EmailSendWechatTool implements WechatTool {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final EmailSendService emailSendService;
    private final Duration pendingTtl;
    private final Map<String, PendingEmailSend> pendingSends = new ConcurrentHashMap<>();

    public EmailSendWechatTool(EmailSendService emailSendService) {
        this(emailSendService, Duration.ofMinutes(10));
    }

    @Autowired
    public EmailSendWechatTool(
            EmailSendService emailSendService,
            @Value("${email.pending-ttl:10m}") Duration pendingTtl) {
        this.emailSendService = emailSendService;
        this.pendingTtl = pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()
                ? Duration.ofMinutes(10)
                : pendingTtl;
    }

    @Override
    public String name() {
        return "email_send";
    }

    @Override
    public String description() {
        return "通过已配置的 QQ 邮箱 SMTP，把本地已下载好的文件或代码目录作为附件发送到指定邮箱；执行前必须让用户确认。";
    }

    @Override
    public List<String> arguments() {
        return List.of("to", "subject", "body", "file_path", "confirm");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("to", "收件人邮箱，多个邮箱用逗号分隔", "someone@qq.com"),
                WechatToolParameter.optionalString("subject", "邮件主题", "代码文件"),
                WechatToolParameter.optionalString("body", "邮件正文", "附件是刚才下载好的代码，请查收。"),
                WechatToolParameter.optionalString("file_path", "本地已下载文件或目录路径；如果当前微信消息带文件，可以不填", "data/downloads/demo-project"),
                WechatToolParameter.optionalBoolean("confirm", "用户明确确认发送时为 true；未确认时不要设置为 true", false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "发送本地已下载文件或代码目录到邮箱，适合用户要求“把刚才下载好的代码/文件发到某邮箱”。",
                List.of(
                        "必须有明确收件人，并且有本地文件路径或当前微信消息附件；缺少时先追问，不要猜测路径。",
                        "首次调用只生成确认信息；用户明确确认后才真正发送。",
                        "不能发送任意系统路径，只能发送配置允许目录中的文件。",
                        "不负责收邮件、查邮件或群发。"),
                List.of("to：收件人邮箱", "file_path：本地文件或目录路径；当前消息有文件时可省略", "confirm：用户确认发送后才为 true"),
                List.of("待确认邮件摘要", "邮件发送成功或失败原因"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        cleanupExpired();
        try {
            if (isCancel(request.userText())) {
                pendingSends.remove(request.sessionKey());
                return WechatReply.text("已取消邮件发送。");
            }
            if (isConfirmation(request) && pendingSends.containsKey(request.sessionKey())) {
                PendingEmailSend pending = pendingSends.get(request.sessionKey());
                if (pending.expiresAt().isBefore(Instant.now())) {
                    pendingSends.remove(request.sessionKey());
                    return WechatReply.text("这次邮件发送确认已过期，请重新告诉我收件人和要发送的文件。");
                }
                try {
                    WechatReply reply = sendConfirmed(pending);
                    pendingSends.remove(request.sessionKey());
                    return reply;
                } catch (EmailToolException exception) {
                    return WechatReply.text("""
                            邮件发送失败：%s

                            我已保留这次待发送任务，你修正配置或稍后可以直接回复“重新发送”；不想发了就回复“取消发送”。
                            """.formatted(cleanFailureMessage(exception, "请检查邮箱配置和网络连接。")).strip());
                }
            }
            if (isConfirmation(request) && !hasMaterialArguments(request)) {
                return WechatReply.text("没有待确认的邮件发送任务。请重新告诉我收件人和要发送的文件。");
            }
            PendingEmailSend pending = preparePending(request);
            pendingSends.put(request.sessionKey(), pending);
            return WechatReply.text(confirmText(pending));
        } catch (EmailToolException exception) {
            return WechatReply.text("邮件发送准备失败：" + cleanFailureMessage(exception, "请检查邮箱配置和附件路径。"));
        }
    }

    private PendingEmailSend preparePending(WechatToolRequest request) {
        List<String> recipients = recipients(firstNonBlank(request.argument("to"), request.argument("recipient")));
        if (recipients.isEmpty()) {
            recipients = recipientsFromText(request.userText());
        }
        String filePath = firstNonBlank(
                request.argument("file_path"),
                request.argument("path"),
                request.argument("local_path"),
                request.argument("attachment_path"));
        if (recipients.isEmpty()) {
            throw new EmailToolException("缺少收件人邮箱");
        }
        String subject = firstNonBlank(request.argument("subject"), subjectFromText(request.userText()), "OpenClaw 文件发送");
        String body = normalizeBody(firstNonBlank(
                request.argument("body"),
                request.argument("content"),
                bodyFromText(request.userText()),
                "附件已随邮件发送，请查收。"));
        if (filePath.isBlank() && request.files().isEmpty()) {
            throw new EmailToolException("缺少本地文件路径，当前会话里也没有可发送的最近文件");
        }
        PreparedEmailAttachment attachment = shouldUseWechatFile(request, filePath)
                ? prepareCurrentWechatFile(request)
                : emailSendService.prepareAttachment(Path.of(stripQuotes(filePath)));
        EmailSendRequest sendRequest = new EmailSendRequest(recipients, subject, body, attachment.path());
        return new PendingEmailSend(sendRequest, attachment, Instant.now().plus(pendingTtl));
    }

    private PreparedEmailAttachment prepareCurrentWechatFile(WechatToolRequest request) {
        if (request.files().isEmpty()) {
            throw new EmailToolException("缺少本地文件路径，当前微信消息里也没有可发送的文件");
        }
        WechatIncomingFile file = request.files().get(0);
        return emailSendService.prepareIncomingFile(request.sessionKey(), file);
    }

    private boolean shouldUseWechatFile(WechatToolRequest request, String filePath) {
        if (request.files().isEmpty()) {
            return false;
        }
        if (filePath == null || filePath.isBlank()) {
            return true;
        }
        String text = request.userText() == null ? "" : request.userText().strip().toLowerCase(java.util.Locale.ROOT);
        return text.contains("这个文件")
                || text.contains("这个附件")
                || text.contains("这份文件")
                || text.contains("这份附件")
                || text.contains("当前文件")
                || text.contains("当前附件")
                || text.contains("刚才的文件")
                || text.contains("刚才发的文件")
                || text.contains("刚刚的文件")
                || text.contains("刚刚发的文件")
                || text.contains("刚发的文件")
                || text.contains("收到的文件")
                || text.contains("收到的附件")
                || text.contains("微信文件")
                || text.contains("微信附件")
                || text.contains("上一个文件")
                || text.contains("上一个附件")
                || text.contains("this file")
                || text.contains("this attachment");
    }

    private WechatReply sendConfirmed(PendingEmailSend pending) {
        EmailSendResult result = emailSendService.send(pending.request());
        return WechatReply.text("""
                邮件已发送。

                收件人：%s
                主题：%s
                附件：%s（%s）
                """.formatted(
                String.join(", ", result.to()),
                result.subject(),
                result.attachmentName(),
                formatSize(result.attachmentSizeBytes())).strip());
    }

    private String confirmText(PendingEmailSend pending) {
        return """
                准备发送邮件，请确认：

                收件人：%s
                主题：%s
                附件：%s（%s）%s

                回复“确认发送”后执行；回复“取消发送”则取消。
                """.formatted(
                String.join(", ", pending.request().to()),
                pending.request().subject(),
                pending.attachment().fileName(),
                formatSize(pending.attachment().sizeBytes()),
                pending.attachment().generatedZip() ? "，已自动压缩目录" : "").strip();
    }

    private List<String> recipients(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;，；\\s]+"))
                .filter(item -> item != null && !item.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    private List<String> recipientsFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        List<String> result = new java.util.ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result.stream().distinct().toList();
    }

    private String subjectFromText(String text) {
        return labeledValue(text, List.of("主题是", "主题为", "主题：", "主题:", "标题是", "标题为"));
    }

    private String bodyFromText(String text) {
        return labeledValue(text, List.of("正文就是", "正文是", "正文为", "正文：", "正文:", "内容是", "内容为", "邮件内容是"));
    }

    private String labeledValue(String text, List<String> labels) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String line : text.split("\\R")) {
            String value = line == null ? "" : line.strip();
            if (value.isBlank()) {
                continue;
            }
            for (String label : labels) {
                if (value.startsWith(label)) {
                    return value.substring(label.length()).strip();
                }
            }
        }
        return "";
    }

    private String normalizeBody(String body) {
        String value = body == null ? "" : body.strip();
        if (value.equals("这个文件")
                || value.equals("这份文件")
                || value.equals("这个附件")
                || value.equals("这份附件")
                || value.equals("附件")) {
            return "附件已随邮件发送，请查收。";
        }
        return value.isBlank() ? "附件已随邮件发送，请查收。" : value;
    }

    private boolean isConfirmation(WechatToolRequest request) {
        String text = request.userText() == null ? "" : request.userText().strip();
        return request.booleanArgument("confirm")
                || text.equals("确认")
                || text.equals("确认发送")
                || text.equals("重新发送")
                || text.equals("重试")
                || text.equals("重试发送")
                || text.equals("可以发送")
                || text.equals("发送吧")
                || text.contains("确认发送邮件");
    }

    private boolean isCancel(String text) {
        String value = text == null ? "" : text.strip();
        return value.equals("取消")
                || value.equals("取消发送")
                || value.contains("不要发送")
                || value.contains("别发");
    }

    private boolean hasMaterialArguments(WechatToolRequest request) {
        return !firstNonBlank(request.argument("to"), request.argument("recipient")).isBlank()
                || !firstNonBlank(
                request.argument("file_path"),
                request.argument("path"),
                request.argument("local_path"),
                request.argument("attachment_path")).isBlank();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingSends.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String stripQuotes(String value) {
        String clean = value == null ? "" : value.strip();
        while (clean.length() >= 2) {
            boolean doubleQuoted = clean.startsWith("\"") && clean.endsWith("\"");
            boolean singleQuoted = clean.startsWith("'") && clean.endsWith("'");
            if (!doubleQuoted && !singleQuoted) {
                break;
            }
            clean = clean.substring(1, clean.length() - 1).strip();
        }
        return clean;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + "KB";
        }
        return "%.2fMB".formatted(bytes / 1024.0 / 1024.0);
    }

    private String messageOrDefault(Exception exception, String defaultMessage) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
    }

    private String cleanFailureMessage(Exception exception, String defaultMessage) {
        String message = messageOrDefault(exception, defaultMessage);
        for (String prefix : List.of("邮件发送失败：", "邮件发送准备失败：")) {
            if (message.startsWith(prefix)) {
                return message.substring(prefix.length()).strip();
            }
        }
        return message;
    }

    private record PendingEmailSend(
            EmailSendRequest request,
            PreparedEmailAttachment attachment,
            Instant expiresAt) {
    }
}
