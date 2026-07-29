package com.example.spring.wechat.email.service;

import com.example.spring.wechat.email.EmailToolException;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.DownloadedEmailAttachment;
import com.example.spring.wechat.email.model.EmailMessageDetail;
import com.example.spring.wechat.email.model.EmailMessageSummary;
import com.example.spring.wechat.email.model.EmailUnreadBatchResult;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@Service
public class EmailReceiveService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EmailProperties properties;

    public EmailReceiveService(EmailProperties properties) {
        this.properties = properties;
    }

    public int defaultLimit() {
        return properties.getQueryLimit();
    }

    public List<EmailMessageSummary> listRecent(int limit) {
        validateReceiveEnabled();
        int safeLimit = safeLimit(limit);
        try (Mailbox mailbox = openInbox()) {
            int count = mailbox.folder().getMessageCount();
            if (count <= 0) {
                return List.of();
            }
            int start = Math.max(1, count - safeLimit + 1);
            Message[] messages = mailbox.folder().getMessages(start, count);
            mailbox.folder().fetch(messages, envelopeProfile());
            List<EmailMessageSummary> result = new ArrayList<>();
            for (int index = messages.length - 1; index >= 0; index--) {
                result.add(toSummary(mailbox.uidFolder(), messages[index], true));
            }
            return result;
        } catch (MessagingException | IOException exception) {
            throw new EmailToolException("查询最近邮件失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    public List<EmailMessageSummary> search(String keyword, String from, boolean unreadOnly, int limit) {
        validateReceiveEnabled();
        int safeLimit = safeLimit(limit);
        int scanLimit = Math.max(safeLimit, Math.min(properties.getQueryScanLimit(), 500));
        String normalizedKeyword = normalizeSearchText(keyword);
        String normalizedFrom = normalizeSearchText(from);
        try (Mailbox mailbox = openInbox()) {
            int count = mailbox.folder().getMessageCount();
            if (count <= 0) {
                return List.of();
            }
            int start = Math.max(1, count - scanLimit + 1);
            Message[] messages = mailbox.folder().getMessages(start, count);
            mailbox.folder().fetch(messages, envelopeProfile());
            List<EmailMessageSummary> result = new ArrayList<>();
            for (int index = messages.length - 1; index >= 0 && result.size() < safeLimit; index--) {
                Message message = messages[index];
                EmailMessageSummary summary = toSummary(mailbox.uidFolder(), message, !normalizedKeyword.isBlank());
                if (unreadOnly && !summary.unread()) {
                    continue;
                }
                if (!normalizedFrom.isBlank() && !normalizeSearchText(summary.from()).contains(normalizedFrom)) {
                    continue;
                }
                if (!normalizedKeyword.isBlank()
                        && !normalizeSearchText(summary.subject()).contains(normalizedKeyword)
                        && !normalizeSearchText(summary.preview()).contains(normalizedKeyword)
                        && !normalizeSearchText(summary.from()).contains(normalizedKeyword)) {
                    continue;
                }
                result.add(summary);
            }
            return result;
        } catch (MessagingException | IOException exception) {
            throw new EmailToolException("搜索邮件失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    public EmailMessageDetail read(String uid) {
        return read(uid, false);
    }

    public EmailMessageDetail read(String uid, boolean markRead) {
        validateReceiveEnabled();
        long messageUid = parseUid(uid);
        try (Mailbox mailbox = openInbox(markRead)) {
            Message message = mailbox.uidFolder().getMessageByUID(messageUid);
            if (message == null) {
                throw new EmailToolException("没有找到这封邮件：" + uid);
            }
            boolean unread = !message.isSet(Flags.Flag.SEEN);
            String text = extractText(message).strip();
            if (markRead && unread) {
                message.setFlag(Flags.Flag.SEEN, true);
            }
            return new EmailMessageDetail(
                    String.valueOf(messageUid),
                    addresses(message.getFrom()),
                    addressList(message.getRecipients(Message.RecipientType.TO)),
                    decode(message.getSubject()),
                    sentAt(message),
                    markRead ? false : unread,
                    countAttachments(message),
                    text.isBlank() ? "这封邮件没有可读取的正文内容。" : limitText(text, 3000));
        } catch (EmailToolException exception) {
            throw exception;
        } catch (MessagingException | IOException exception) {
            throw new EmailToolException("读取邮件失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    public void markRead(String uid) {
        validateReceiveEnabled();
        long messageUid = parseUid(uid);
        try (Mailbox mailbox = openInbox(true)) {
            Message message = mailbox.uidFolder().getMessageByUID(messageUid);
            if (message == null) {
                throw new EmailToolException("没有找到这封邮件：" + uid);
            }
            message.setFlag(Flags.Flag.SEEN, true);
        } catch (EmailToolException exception) {
            throw exception;
        } catch (MessagingException exception) {
            throw new EmailToolException("标记邮件已读失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    public EmailUnreadBatchResult readUnreadAndMarkRead(int limit) {
        validateReceiveEnabled();
        int safeLimit = Math.min(Math.max(limit, 1), 5);
        try (Mailbox mailbox = openInbox(true)) {
            Message[] unreadMessages = mailbox.folder().search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            if (unreadMessages == null || unreadMessages.length == 0) {
                return new EmailUnreadBatchResult(0, List.of());
            }
            mailbox.folder().fetch(unreadMessages, envelopeProfile());
            List<EmailMessageDetail> details = new ArrayList<>();
            for (int index = unreadMessages.length - 1; index >= 0 && details.size() < safeLimit; index--) {
                Message message = unreadMessages[index];
                long uid = mailbox.uidFolder().getUID(message);
                String text = extractText(message).strip();
                details.add(new EmailMessageDetail(
                        String.valueOf(uid),
                        addresses(message.getFrom()),
                        addressList(message.getRecipients(Message.RecipientType.TO)),
                        decode(message.getSubject()),
                        sentAt(message),
                        false,
                        countAttachments(message),
                        text.isBlank() ? "这封邮件没有可读取的正文内容。" : limitText(text, 1200)));
                message.setFlag(Flags.Flag.SEEN, true);
            }
            return new EmailUnreadBatchResult(unreadMessages.length, details);
        } catch (MessagingException | IOException exception) {
            throw new EmailToolException("批量读取未读邮件失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    public List<DownloadedEmailAttachment> downloadAttachments(String uid, Integer attachmentIndex) {
        validateReceiveEnabled();
        long messageUid = parseUid(uid);
        try (Mailbox mailbox = openInbox()) {
            Message message = mailbox.uidFolder().getMessageByUID(messageUid);
            if (message == null) {
                throw new EmailToolException("没有找到这封邮件：" + uid);
            }
            List<AttachmentPart> attachments = new ArrayList<>();
            collectAttachments(message, attachments);
            if (attachments.isEmpty()) {
                throw new EmailToolException("这封邮件没有附件");
            }
            List<AttachmentPart> selected = selectAttachments(attachments, attachmentIndex);
            List<DownloadedEmailAttachment> result = new ArrayList<>();
            for (AttachmentPart attachment : selected) {
                result.add(saveAttachment(String.valueOf(messageUid), attachment));
            }
            return result;
        } catch (EmailToolException exception) {
            throw exception;
        } catch (MessagingException | IOException exception) {
            throw new EmailToolException("下载邮件附件失败：" + friendlyReceiveMessage(rootMessage(exception)), exception);
        }
    }

    private Mailbox openInbox() throws MessagingException {
        return openInbox(false);
    }

    private Mailbox openInbox(boolean readWrite) throws MessagingException {
        Properties props = new Properties();
        String protocol = properties.isImapSsl() ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        props.put("mail.imap.connectiontimeout", "15000");
        props.put("mail.imap.timeout", "30000");
        props.put("mail.imap.writetimeout", "30000");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");
        props.put("mail.imaps.writetimeout", "30000");
        if (properties.isImapSsl()) {
            props.put("mail.imap.ssl.enable", "true");
            props.put("mail.imaps.ssl.enable", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(properties.getImapHost(), properties.getImapPort(), properties.getUsername(), properties.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(readWrite ? Folder.READ_WRITE : Folder.READ_ONLY);
        if (!(folder instanceof UIDFolder uidFolder)) {
            throw new EmailToolException("当前 IMAP 服务不支持 UID 查询");
        }
        return new Mailbox(store, folder, uidFolder);
    }

    private FetchProfile envelopeProfile() {
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        return profile;
    }

    private EmailMessageSummary toSummary(UIDFolder uidFolder, Message message, boolean includePreview)
            throws MessagingException, IOException {
        String preview = includePreview ? previewText(message) : "";
        return new EmailMessageSummary(
                String.valueOf(uidFolder.getUID(message)),
                addresses(message.getFrom()),
                decode(message.getSubject()),
                sentAt(message),
                !message.isSet(Flags.Flag.SEEN),
                hasAttachments(message),
                countAttachments(message),
                preview);
    }

    private Instant sentAt(Message message) throws MessagingException {
        if (message.getSentDate() != null) {
            return message.getSentDate().toInstant();
        }
        if (message.getReceivedDate() != null) {
            return message.getReceivedDate().toInstant();
        }
        return Instant.EPOCH;
    }

    private String previewText(Message message) throws MessagingException, IOException {
        return limitText(extractText(message).replaceAll("\\s+", " ").strip(), 120);
    }

    private String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content == null ? "" : content.toString();
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return htmlToText(content == null ? "" : content.toString());
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart multipart)) {
                return "";
            }
            StringBuilder plain = new StringBuilder();
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (isAttachment(bodyPart)) {
                    continue;
                }
                String value = extractText(bodyPart);
                if (bodyPart.isMimeType("text/html")) {
                    appendText(html, value);
                } else {
                    appendText(plain, value);
                }
            }
            return !plain.isEmpty() ? plain.toString() : html.toString();
        }
        return "";
    }

    private boolean hasAttachments(Part part) throws MessagingException, IOException {
        return countAttachments(part) > 0;
    }

    private int countAttachments(Part part) throws MessagingException, IOException {
        List<AttachmentPart> attachments = new ArrayList<>();
        collectAttachments(part, attachments);
        return attachments.size();
    }

    private void collectAttachments(Part part, List<AttachmentPart> attachments)
            throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart multipart)) {
                return;
            }
            for (int i = 0; i < multipart.getCount(); i++) {
                collectAttachments(multipart.getBodyPart(i), attachments);
            }
            return;
        }
        if (!isAttachment(part)) {
            return;
        }
        String fileName = decode(part.getFileName());
        if (fileName.isBlank()) {
            fileName = "attachment-" + (attachments.size() + 1);
        }
        attachments.add(new AttachmentPart(fileName, part.getContentType(), part));
    }

    private boolean isAttachment(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        String fileName = part.getFileName();
        return Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || Part.INLINE.equalsIgnoreCase(disposition) && fileName != null && !fileName.isBlank()
                || fileName != null && !fileName.isBlank();
    }

    private List<AttachmentPart> selectAttachments(List<AttachmentPart> attachments, Integer attachmentIndex) {
        if (attachmentIndex == null || attachmentIndex <= 0) {
            return attachments;
        }
        if (attachmentIndex > attachments.size()) {
            throw new EmailToolException("附件序号不存在，当前共有 " + attachments.size() + " 个附件");
        }
        return List.of(attachments.get(attachmentIndex - 1));
    }

    private DownloadedEmailAttachment saveAttachment(String messageUid, AttachmentPart attachment)
            throws IOException, MessagingException {
        Path dir = normalize(properties.getAttachmentDownloadDir())
                .resolve(LocalDate.now().format(DAY))
                .resolve(safeFileName(messageUid));
        Files.createDirectories(dir);
        Path target = uniquePath(dir.resolve(safeFileName(attachment.fileName())));
        try (InputStream input = attachment.part().getInputStream()) {
            Files.copy(input, target);
        }
        byte[] bytes = Files.readAllBytes(target);
        return new DownloadedEmailAttachment(
                messageUid,
                target.getFileName().toString(),
                contentTypeOnly(attachment.contentType()),
                target,
                bytes,
                bytes.length);
    }

    private Path uniquePath(Path target) throws IOException {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String base = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        for (int i = 2; i < 1000; i++) {
            Path candidate = target.getParent().resolve(base + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return Files.createTempFile(target.getParent(), base + "-", extension);
    }

    private void validateReceiveEnabled() {
        if (!properties.isReceiveEnabled()) {
            throw new EmailToolException("邮箱查询功能未启用");
        }
        if (properties.getUsername().isBlank() || properties.getPassword().isBlank()) {
            throw new EmailToolException("邮箱 IMAP 未配置完整，请检查 EMAIL_USERNAME 和 EMAIL_PASSWORD");
        }
        if (properties.getImapHost().isBlank()) {
            throw new EmailToolException("邮箱 IMAP 地址未配置，请检查 EMAIL_IMAP_HOST");
        }
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return properties.getQueryLimit();
        }
        return Math.min(limit, 20);
    }

    private long parseUid(String value) {
        if (value == null || value.isBlank()) {
            throw new EmailToolException("缺少邮件编号，请先查询邮件列表，再指定要读取的邮件");
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new EmailToolException("邮件编号格式不正确：" + value);
        }
    }

    private String addresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return InternetAddress.toUnicodeString(addresses);
    }

    private List<String> addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Address address : addresses) {
            result.add(address == null ? "" : address.toString());
        }
        return result.stream().filter(value -> !value.isBlank()).toList();
    }

    private String decode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return MimeUtility.decodeText(value).strip();
        } catch (Exception exception) {
            return value.strip();
        }
    }

    private String htmlToText(String html) {
        return html == null
                ? ""
                : html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private void appendText(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.strip());
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private String limitText(String text, int maxLength) {
        String value = text == null ? "" : text.strip();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeFileName(String value) {
        String clean = value == null || value.isBlank() ? "attachment" : value.strip();
        return clean.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private Path normalize(Path path) {
        Path value = path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path);
        return value.normalize().toAbsolutePath();
    }

    private String contentTypeOnly(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator)).strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String friendlyReceiveMessage(String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage.strip();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("authentication") || lower.contains("login") || lower.contains("535")
                || lower.contains("bad credentials")) {
            return "IMAP 登录失败。QQ 邮箱需要使用授权码，并确认已开启 IMAP/SMTP 服务。原始信息：" + raw;
        }
        if (lower.contains("connection refused") || lower.contains("could not connect")) {
            return "无法连接 IMAP 服务器，请检查 EMAIL_IMAP_HOST、EMAIL_IMAP_PORT 和网络。原始信息：" + raw;
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连接 IMAP 服务器超时，请检查网络或稍后重试。原始信息：" + raw;
        }
        return raw.isBlank() ? "未知 IMAP 错误" : raw;
    }

    private record Mailbox(Store store, Folder folder, UIDFolder uidFolder) implements AutoCloseable {
        @Override
        public void close() {
            try {
                if (folder != null && folder.isOpen()) {
                    folder.close(false);
                }
            } catch (MessagingException ignored) {
            }
            try {
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (MessagingException ignored) {
            }
        }
    }

    private record AttachmentPart(String fileName, String contentType, Part part) {
    }
}
