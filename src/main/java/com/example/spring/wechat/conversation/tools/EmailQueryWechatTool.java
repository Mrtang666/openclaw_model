package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.email.EmailToolException;
import com.example.spring.wechat.email.model.DownloadedEmailAttachment;
import com.example.spring.wechat.email.model.EmailMessageDetail;
import com.example.spring.wechat.email.model.EmailMessageSummary;
import com.example.spring.wechat.email.model.EmailUnreadBatchResult;
import com.example.spring.wechat.email.service.EmailReceiveService;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailQueryWechatTool implements WechatTool {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EmailReceiveService emailReceiveService;
    private final Map<String, List<EmailMessageSummary>> lastResults = new ConcurrentHashMap<>();

    public EmailQueryWechatTool(EmailReceiveService emailReceiveService) {
        this.emailReceiveService = emailReceiveService;
    }

    @Override
    public String name() {
        return "email_query";
    }

    @Override
    public String description() {
        return "通过已配置的邮箱 IMAP 查询收件箱邮件，支持最近邮件、关键词搜索、读取邮件正文、标记已读和下载邮件附件。";
    }

    @Override
    public List<String> arguments() {
        return List.of("action", "query", "from", "limit", "unread_only", "message_id", "mail_index", "attachment_index", "mark_read");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "action",
                        "操作类型：list=最近邮件，search=搜索邮件，read=读取正文，read_unread=读取最多5封未读并标记已读，mark_read=标记已读，download_attachments=下载附件",
                        List.of("list", "search", "read", "read_unread", "mark_read", "download_attachments"),
                        "list"),
                WechatToolParameter.optionalString("query", "搜索关键词，可匹配主题、发件人和正文预览", "测试邮件"),
                WechatToolParameter.optionalString("from", "发件人关键词或邮箱", "someone@qq.com"),
                WechatToolParameter.optionalString("limit", "返回邮件数量，最多 20", "5"),
                WechatToolParameter.optionalBoolean("unread_only", "是否只查未读邮件", false),
                WechatToolParameter.optionalString("message_id", "邮件 UID；也可以传上次查询列表中的序号", "1"),
                WechatToolParameter.optionalString("mail_index", "上次查询列表中的邮件序号", "1"),
                WechatToolParameter.optionalString("attachment_index", "附件序号；不填表示下载全部附件", "1"),
                WechatToolParameter.optionalBoolean("mark_read", "读取正文后是否标记为已读；默认不修改邮件状态", false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "查询邮箱收件箱，读取邮件内容，下载邮件附件并返回给微信用户。",
                List.of(
                        "只读访问收件箱，不删除邮件，不主动标记已读。",
                        "只有用户明确要求标记已读，或参数 mark_read=true 时，才会修改邮件已读状态。",
                        "读取或下载附件前需要邮件编号；如果用户说“第一封、上一封”，优先使用上次查询结果。",
                        "不能查询未配置的邮箱，也不能绕过邮箱 IMAP 授权。"),
                List.of("action：要执行的邮箱操作；read/mark_read/download_attachments 需要 message_id 或 mail_index；read_unread 一次最多处理5封"),
                List.of("邮件列表", "单封邮件摘要", "邮件附件文件"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            String action = inferAction(request);
            return switch (action) {
                case "search" -> search(request);
                case "read" -> read(request);
                case "read_unread" -> readUnreadAndMarkRead(request);
                case "mark_read" -> markRead(request);
                case "download_attachments" -> downloadAttachments(request);
                default -> listRecent(request);
            };
        } catch (EmailToolException exception) {
            return WechatReply.text("邮件查询失败：" + cleanMessage(exception));
        }
    }

    private WechatReply listRecent(WechatToolRequest request) {
        int limit = limit(request);
        List<EmailMessageSummary> messages = emailReceiveService.listRecent(limit);
        lastResults.put(request.sessionKey(), messages);
        return WechatReply.text(formatList("最近邮件", messages));
    }

    private WechatReply search(WechatToolRequest request) {
        int limit = limit(request);
        String query = firstNonBlank(request.argument("query"), queryFromText(request.userText()));
        String from = firstNonBlank(request.argument("from"), fromFromText(request.userText()));
        boolean unreadOnly = request.booleanArgument("unread_only") || containsAny(request.userText(), "未读", "unread");
        List<EmailMessageSummary> messages = emailReceiveService.search(query, from, unreadOnly, limit);
        lastResults.put(request.sessionKey(), messages);
        String title = firstNonBlank(query, from).isBlank() && !unreadOnly
                ? "搜索结果"
                : "邮件搜索结果";
        return WechatReply.text(formatList(title, messages));
    }

    private WechatReply read(WechatToolRequest request) {
        String uid = resolveMessageUid(request);
        boolean markRead = shouldMarkRead(request);
        EmailMessageDetail detail = emailReceiveService.read(uid, markRead);
        lastResults.put(request.sessionKey(), List.of(toSummary(detail)));
        String text = formatDetail(detail);
        if (markRead) {
            text = text + "\n\n已将这封邮件标记为已读。";
        }
        return WechatReply.text(text);
    }

    private WechatReply markRead(WechatToolRequest request) {
        String uid = resolveMessageUid(request);
        emailReceiveService.markRead(uid);
        return WechatReply.text("已将这封邮件标记为已读。\n编号：" + uid);
    }

    private WechatReply readUnreadAndMarkRead(WechatToolRequest request) {
        EmailUnreadBatchResult result = emailReceiveService.readUnreadAndMarkRead(5);
        lastResults.put(request.sessionKey(), result.messages().stream().map(this::toSummary).toList());
        return WechatReply.text(formatUnreadBatch(result));
    }

    private WechatReply downloadAttachments(WechatToolRequest request) {
        String uid = resolveMessageUid(request);
        Integer attachmentIndex = positiveInt(firstNonBlank(
                request.argument("attachment_index"),
                request.argument("attachmentIndex"),
                attachmentIndexFromText(request.userText())));
        List<DownloadedEmailAttachment> attachments = emailReceiveService.downloadAttachments(uid, attachmentIndex);
        List<WechatReply.Part> parts = new ArrayList<>();
        parts.add(WechatReply.Part.text("""
                已下载邮件附件，共 %d 个。

                %s
                """.formatted(
                attachments.size(),
                attachments.stream()
                        .map(attachment -> "- " + attachment.fileName() + "（" + formatSize(attachment.sizeBytes()) + "）")
                        .collect(java.util.stream.Collectors.joining("\n"))).strip()));
        for (DownloadedEmailAttachment attachment : attachments) {
            parts.add(WechatReply.Part.file(new WechatReply.FileAttachment(
                    attachment.bytes(),
                    attachment.fileName(),
                    attachment.contentType(),
                    "邮件附件：" + attachment.fileName())));
        }
        return WechatReply.ordered(parts);
    }

    private String inferAction(WechatToolRequest request) {
        String action = firstNonBlank(request.argument("action"), request.argument("operation")).toLowerCase(java.util.Locale.ROOT);
        if (!action.isBlank()) {
            if (action.equals("download") || action.equals("attachment") || action.equals("attachments")) {
                return "download_attachments";
            }
            if (action.equals("markread") || action.equals("mark_read") || action.equals("readed")) {
                return "mark_read";
            }
            if (action.equals("readunread") || action.equals("read_unread") || action.equals("read_unread_and_mark_read")) {
                return "read_unread";
            }
            return action;
        }
        String text = request.userText() == null ? "" : request.userText().strip().toLowerCase(java.util.Locale.ROOT);
        if (containsAny(text, "附件", "下载", "发给我", "attachment", "download")) {
            return "download_attachments";
        }
        if (isReadAllUnreadRequest(text)) {
            return "read_unread";
        }
        if (containsAny(text, "读取", "读一下", "打开", "看看", "内容", "正文", "read")) {
            return "read";
        }
        if (containsAny(text, "标记已读", "设为已读", "标为已读", "标成已读", "置为已读", "mark read", "mark as read")) {
            return "mark_read";
        }
        if (containsAny(text, "搜索", "查找", "包含", "关于", "from:", "search")) {
            return "search";
        }
        if (containsAny(text, "未读", "unread")) {
            return "search";
        }
        return "list";
    }

    private boolean isReadAllUnreadRequest(String text) {
        if (text == null || text.isBlank() || !text.contains("未读")) {
            return false;
        }
        boolean all = containsAny(text, "全部", "所有", "全都", "都");
        boolean readOrMark = containsAny(text, "读取", "读一下", "打开", "看看", "内容", "正文", "标记已读", "设为已读", "read", "mark read");
        return all && readOrMark;
    }

    private boolean shouldMarkRead(WechatToolRequest request) {
        String text = request.userText() == null ? "" : request.userText().strip().toLowerCase(java.util.Locale.ROOT);
        return request.booleanArgument("mark_read")
                || request.booleanArgument("markRead")
                || containsAny(text, "标记已读", "设为已读", "标为已读", "标成已读", "置为已读", "mark read", "mark as read");
    }

    private String resolveMessageUid(WechatToolRequest request) {
        String ordinalText = firstNonBlank(
                request.argument("mail_index"),
                request.argument("index"),
                ordinalFromText(request.userText()));
        Integer ordinal = positiveInt(ordinalText);
        if (ordinal != null) {
            List<EmailMessageSummary> messages = lastResults.getOrDefault(request.sessionKey(), List.of());
            if (ordinal <= messages.size()) {
                return messages.get(ordinal - 1).uid();
            }
            throw new EmailToolException("邮件序号不存在，请先查询邮件列表。当前列表共有 " + messages.size() + " 封邮件");
        }

        String messageId = firstNonBlank(request.argument("message_id"), request.argument("uid"), request.argument("mail_uid"));
        if (!messageId.isBlank()) {
            Integer maybeOrdinal = positiveInt(messageId);
            List<EmailMessageSummary> messages = lastResults.getOrDefault(request.sessionKey(), List.of());
            if (maybeOrdinal != null && maybeOrdinal <= messages.size()) {
                return messages.get(maybeOrdinal - 1).uid();
            }
            return messageId;
        }

        List<EmailMessageSummary> messages = lastResults.getOrDefault(request.sessionKey(), List.of());
        if (messages.size() == 1) {
            return messages.get(0).uid();
        }
        throw new EmailToolException("缺少邮件编号。你可以先说“查最近邮件”，再说“读取第一封”或“下载第一封附件”。");
    }

    private String formatList(String title, List<EmailMessageSummary> messages) {
        if (messages == null || messages.isEmpty()) {
            return title + "：没有找到匹配邮件。";
        }
        StringBuilder text = new StringBuilder(title).append("：\n\n");
        for (int i = 0; i < messages.size(); i++) {
            EmailMessageSummary message = messages.get(i);
            text.append(i + 1)
                    .append(". ")
                    .append(message.unread() ? "[未读] " : "")
                    .append(firstNonBlank(message.subject(), "无主题"))
                    .append('\n')
                    .append("发件人：").append(firstNonBlank(message.from(), "未知")).append('\n')
                    .append("时间：").append(formatTime(message.sentAt())).append('\n')
                    .append("编号：").append(message.uid()).append('\n');
            if (message.hasAttachments()) {
                text.append("附件：").append(message.attachmentCount()).append(" 个\n");
            }
            if (!message.preview().isBlank()) {
                text.append("预览：").append(message.preview()).append('\n');
            }
            if (i < messages.size() - 1) {
                text.append('\n');
            }
        }
        text.append("\n可以继续说“读取第一封”或“下载第一封附件”。");
        return text.toString().strip();
    }

    private String formatDetail(EmailMessageDetail detail) {
        return """
                邮件内容：

                主题：%s
                发件人：%s
                时间：%s
                编号：%s
                附件：%d 个

                %s
                """.formatted(
                firstNonBlank(detail.subject(), "无主题"),
                firstNonBlank(detail.from(), "未知"),
                formatTime(detail.sentAt()),
                detail.uid(),
                detail.attachmentCount(),
                detail.text()).strip();
    }

    private String formatUnreadBatch(EmailUnreadBatchResult result) {
        if (result.totalUnread() <= 0 || result.messages().isEmpty()) {
            return "当前没有未读邮件。";
        }
        StringBuilder text = new StringBuilder();
        text.append("当前最多一次读取并标记 5 封未读邮件。\n")
                .append("读取前共有 ").append(result.totalUnread()).append(" 封未读邮件；")
                .append("本次已读取并标记 ").append(result.messages().size()).append(" 封；")
                .append("剩余 ").append(result.remainingUnread()).append(" 封未读。\n\n");
        for (int i = 0; i < result.messages().size(); i++) {
            EmailMessageDetail message = result.messages().get(i);
            text.append(i + 1)
                    .append(". ")
                    .append(firstNonBlank(message.subject(), "无主题"))
                    .append('\n')
                    .append("发件人：").append(firstNonBlank(message.from(), "未知")).append('\n')
                    .append("时间：").append(formatTime(message.sentAt())).append('\n')
                    .append("编号：").append(message.uid()).append('\n');
            if (message.attachmentCount() > 0) {
                text.append("附件：").append(message.attachmentCount()).append(" 个\n");
            }
            text.append("正文：").append(limitText(message.text(), 600)).append('\n');
            if (i < result.messages().size() - 1) {
                text.append('\n');
            }
        }
        return text.toString().strip();
    }

    private EmailMessageSummary toSummary(EmailMessageDetail detail) {
        return new EmailMessageSummary(
                detail.uid(),
                detail.from(),
                detail.subject(),
                detail.sentAt(),
                detail.unread(),
                detail.attachmentCount() > 0,
                detail.attachmentCount(),
                "");
    }

    private int limit(WechatToolRequest request) {
        Integer value = positiveInt(firstNonBlank(request.argument("limit"), request.argument("count")));
        if (value == null) {
            return emailReceiveService.defaultLimit();
        }
        return Math.min(value, 20);
    }

    private Integer positiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.strip());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String ordinalFromText(String text) {
        String value = text == null ? "" : text.strip();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*封").matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (containsAny(value, "第一封", "第一个", "第一条")) {
            return "1";
        }
        if (containsAny(value, "第二封", "第二个", "第二条")) {
            return "2";
        }
        if (containsAny(value, "第三封", "第三个", "第三条")) {
            return "3";
        }
        matcher = java.util.regex.Pattern.compile("^(\\d+)$").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String attachmentIndexFromText(String text) {
        String value = text == null ? "" : text.strip();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*个附件").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String queryFromText(String text) {
        String value = text == null ? "" : text.strip();
        for (String marker : List.of("搜索", "查找", "查询", "包含", "关于")) {
            int index = value.indexOf(marker);
            if (index >= 0 && index + marker.length() < value.length()) {
                return value.substring(index + marker.length())
                        .replace("的邮件", "")
                        .replace("邮件", "")
                        .strip();
            }
        }
        return "";
    }

    private String fromFromText(String text) {
        String value = text == null ? "" : text.strip();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("来自\\s*([^\\s，,。]+)")
                .matcher(value);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private boolean containsAny(String text, String... tokens) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
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

    private String formatTime(java.time.Instant instant) {
        if (instant == null || instant.equals(java.time.Instant.EPOCH)) {
            return "未知";
        }
        return TIME_FORMAT.format(instant);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + "KB";
        }
        return "%.2fMB".formatted(bytes / 1024.0 / 1024.0);
    }

    private String limitText(String text, int maxLength) {
        String value = text == null ? "" : text.strip();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String cleanMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "请检查邮箱配置和网络连接。";
        }
        for (String prefix : List.of("邮件查询失败：", "查询最近邮件失败：", "搜索邮件失败：", "读取邮件失败：", "下载邮件附件失败：")) {
            if (message.startsWith(prefix)) {
                return message.substring(prefix.length()).strip();
            }
        }
        return message;
    }
}
