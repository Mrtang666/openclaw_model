package com.example.spring.wechat.email.service;

import com.example.spring.wechat.email.EmailToolException;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailSendRequest;
import com.example.spring.wechat.email.model.EmailSendResult;
import com.example.spring.wechat.email.model.PreparedEmailAttachment;
import com.example.spring.wechat.model.WechatIncomingFile;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//执行邮件发送
@Service
public class EmailSendService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EmailProperties properties;

    public EmailSendService(EmailProperties properties) {
        this.properties = properties;
    }

    public PreparedEmailAttachment prepareAttachment(Path inputPath) {
        validateEnabled();
        if (inputPath == null) {
            throw new EmailToolException("缺少要发送的本地文件路径");
        }

        Path path = normalize(inputPath);
        ensureAllowed(path);
        if (!Files.exists(path)) {
            throw new EmailToolException("文件不存在：" + path);
        }

        try {
            if (Files.isDirectory(path)) {
                return zipDirectory(path);
            }
            if (!Files.isRegularFile(path)) {
                throw new EmailToolException("只能发送普通文件或目录：" + path);
            }
            long size = Files.size(path);
            ensureSize(size, path.getFileName().toString());
            return new PreparedEmailAttachment(path, path.getFileName().toString(), size, false);
        } catch (IOException exception) {
            throw new EmailToolException("读取附件失败：" + rootMessage(exception), exception);
        }
    }

    public PreparedEmailAttachment prepareIncomingFile(String sessionKey, WechatIncomingFile file) {
        validateEnabled();
        if (file == null || !file.hasBytes()) {
            throw new EmailToolException("当前微信消息里没有可发送的文件内容");
        }
        try {
            ensureSize(file.bytes().length, file.fileName());
            String userPart = safeFileName(sessionKey == null || sessionKey.isBlank() ? "default" : sessionKey);
            String hashPart = file.sha256() == null || file.sha256().isBlank() ? UUID.randomUUID().toString() : file.sha256();
            Path dir = normalize(properties.getWorkDir())
                    .resolve(LocalDate.now().format(DAY))
                    .resolve(userPart)
                    .resolve(hashPart);
            Files.createDirectories(dir);
            Path target = dir.resolve(safeFileName(file.fileName()));
            Files.write(target, file.bytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new PreparedEmailAttachment(target, target.getFileName().toString(), Files.size(target), false);
        } catch (IOException exception) {
            throw new EmailToolException("保存微信文件到邮箱附件目录失败：" + rootMessage(exception), exception);
        }
    }

    public EmailSendResult send(EmailSendRequest request) {
        validateEnabled();
        validateRecipients(request.to());
        PreparedEmailAttachment attachment = prepareAttachment(request.attachmentPath());
        try {
            JavaMailSenderImpl sender = mailSender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getFrom());
            helper.setTo(request.to().toArray(String[]::new));
            helper.setSubject(request.subject());
            helper.setText(bodyOrDefault(request.body()), false);
            helper.addAttachment(attachment.fileName(), new FileSystemResource(attachment.path()));
            sender.send(message);
            return new EmailSendResult(request.to(), request.subject(), attachment.fileName(), attachment.sizeBytes());
        } catch (MessagingException exception) {
            throw new EmailToolException("组装邮件失败：" + friendlySmtpMessage(rootMessage(exception)), exception);
        } catch (MailException exception) {
            throw new EmailToolException("邮件发送失败：" + friendlySmtpMessage(rootMessage(exception)), exception);
        }
    }

    private PreparedEmailAttachment zipDirectory(Path directory) throws IOException {
        String baseName = directory.getFileName() == null ? "attachment" : directory.getFileName().toString();
        Path outputDir = normalize(properties.getWorkDir())
                .resolve(LocalDate.now().format(DAY))
                .resolve(UUID.randomUUID().toString());
        Files.createDirectories(outputDir);
        Path zipPath = outputDir.resolve(safeFileName(baseName) + ".zip");

        try (OutputStream output = Files.newOutputStream(zipPath, StandardOpenOption.CREATE_NEW);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (shouldSkip(directory, path)) {
                        continue;
                    }
                    String entryName = directory.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }

        long size = Files.size(zipPath);
        ensureSize(size, zipPath.getFileName().toString());
        return new PreparedEmailAttachment(zipPath, zipPath.getFileName().toString(), size, true);
    }

    private boolean shouldSkip(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.equals(".git")
                    || name.equals(".idea")
                    || name.equals("node_modules")
                    || name.equals("target")
                    || name.equals("build")
                    || name.equals(".gradle")) {
                return true;
            }
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".log")
                || fileName.endsWith(".tmp")
                || fileName.endsWith(".class");
    }

    private JavaMailSenderImpl mailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", "true");
        javaMailProperties.put("mail.smtp.from", properties.getFrom());
        javaMailProperties.put("mail.smtp.connectiontimeout", "15000");
        javaMailProperties.put("mail.smtp.timeout", "30000");
        javaMailProperties.put("mail.smtp.writetimeout", "30000");
        if (properties.isSsl()) {
            javaMailProperties.put("mail.smtp.ssl.enable", "true");
            javaMailProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            javaMailProperties.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    private void validateEnabled() {
        if (!properties.isEnabled()) {
            throw new EmailToolException("邮箱发送功能未启用");
        }
        if (properties.getHost().isBlank()
                || properties.getUsername().isBlank()
                || properties.getPassword().isBlank()
                || properties.getFrom().isBlank()) {
            throw new EmailToolException("邮箱 SMTP 未配置完整，请检查 email.host、email.username、email.password、email.from");
        }
        if (properties.getHost().toLowerCase(Locale.ROOT).contains("qq.com")
                && !properties.getFrom().equalsIgnoreCase(properties.getUsername())) {
            throw new EmailToolException("QQ 邮箱 SMTP 要求 email.from 与 email.username 使用同一个邮箱地址");
        }
        if (!isEmailAddress(properties.getUsername())) {
            throw new EmailToolException("email.username 需要填写完整邮箱地址，例如 name@qq.com");
        }
        if (!isEmailAddress(properties.getFrom())) {
            throw new EmailToolException("email.from 需要填写完整邮箱地址，例如 name@qq.com");
        }
    }

    private void validateRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw new EmailToolException("缺少收件人邮箱");
        }
        for (String recipient : recipients) {
            if (!isEmailAddress(recipient)) {
                throw new EmailToolException("收件人邮箱格式不正确：" + recipient);
            }
        }
    }

    private boolean isEmailAddress(String value) {
        return value != null && value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private void ensureAllowed(Path path) {
        List<Path> allowedRoots = new java.util.ArrayList<>(properties.getAllowedPaths().stream()
                .map(Path::of)
                .map(this::normalize)
                .toList());
        allowedRoots.add(normalize(properties.getWorkDir()));
        boolean allowed = allowedRoots.stream().anyMatch(path::startsWith);
        if (!allowed) {
            throw new EmailToolException("该路径不允许作为邮件附件发送：" + path);
        }
    }

    private void ensureSize(long sizeBytes, String fileName) {
        if (sizeBytes > properties.maxAttachmentBytes()) {
            throw new EmailToolException("附件过大：" + fileName + "，当前 "
                    + formatSize(sizeBytes) + "，上限 " + properties.getMaxAttachmentSizeMb() + "MB");
        }
    }

    private Path normalize(Path path) {
        Path value = path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path);
        return value.normalize().toAbsolutePath();
    }

    private String bodyOrDefault(String body) {
        return body == null || body.isBlank()
                ? "附件已随邮件发送，请查收。"
                : body;
    }

    private String safeFileName(String value) {
        String clean = value == null || value.isBlank() ? "attachment" : value.strip();
        return clean.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + "KB";
        }
        return String.format(Locale.ROOT, "%.2fMB", bytes / 1024.0 / 1024.0);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String friendlySmtpMessage(String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage.strip();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("send command mailfrom first") || lower.contains("mailfrom")) {
            return "SMTP 发件人命令被服务器拒绝。请确认 EMAIL_FROM 和 EMAIL_USERNAME 是同一个 QQ 邮箱，并且服务已重启加载新配置。原始信息：" + raw;
        }
        if (lower.contains("535") || lower.contains("authentication") || lower.contains("auth failed")
                || lower.contains("bad credentials") || lower.contains("login")) {
            return "SMTP 登录失败。QQ 邮箱需要使用“授权码”，不是 QQ 登录密码；请检查 EMAIL_USERNAME 和 EMAIL_PASSWORD。原始信息：" + raw;
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连接 SMTP 服务器超时，请检查网络或稍后重试。原始信息：" + raw;
        }
        if (lower.contains("connection refused") || lower.contains("could not connect")) {
            return "无法连接 SMTP 服务器，请检查 EMAIL_SMTP_HOST、EMAIL_SMTP_PORT 和网络。原始信息：" + raw;
        }
        if (lower.contains("invalid addresses") || lower.contains("rcpt") || lower.contains("recipient")) {
            return "收件人地址被 SMTP 服务器拒绝，请检查收件人邮箱是否正确。原始信息：" + raw;
        }
        return raw.isBlank() ? "未知 SMTP 错误" : raw;
    }
}
