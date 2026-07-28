package com.example.spring.wechat.report;

import org.springframework.stereotype.Service;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WechatReportService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final WechatReportProperties properties;
    private final Map<String, WechatReport> reports = new ConcurrentHashMap<>();
    private volatile int localServerPort;

    public WechatReportService(WechatReportProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public boolean shouldCreateReport(String text) {
        if (!properties.isEnabled() || text == null || text.isBlank()) {
            return false;
        }
        if (containsSensitiveHint(text)) {
            return false;
        }
        String value = text.strip();
        return value.length() >= properties.getTextLengthThreshold()
                || listItemCount(value) > properties.getItemCountThreshold()
                || looksLikeTable(value)
                || looksLikeFormalReport(value);
    }

    public boolean shouldSendSummaryOnly(String userText, String replyText) {
        String reply = replyText == null ? "" : replyText.strip();
        String user = userText == null ? "" : userText.strip();
        return reply.length() >= properties.getVeryLongTextThreshold()
                || user.contains("完整报告")
                || user.contains("生成报告")
                || user.contains("整理成报告")
                || user.contains("汇总报告")
                || user.contains("正式报告");
    }

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        if (event != null && event.getWebServer() != null) {
            localServerPort = event.getWebServer().getPort();
        }
    }

    public WechatReport create(String title, String text) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("微信报告功能未启用");
        }
        cleanupExpired();
        String id = newId();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTtl());
        String safeTitle = firstNonBlank(title, inferTitle(text), "微信回复报告");
        Path dir = normalize(properties.getStorageDir());
        try {
            Files.createDirectories(dir);
            Path path = dir.resolve(id + ".html");
            Files.writeString(path, renderHtml(id, safeTitle, text, now, expiresAt), StandardCharsets.UTF_8);
            WechatReport report = new WechatReport(id, safeTitle, publicUrl(id), path, now, expiresAt);
            reports.put(id, report);
            return report;
        } catch (IOException exception) {
            throw new IllegalStateException("生成微信报告失败：" + rootMessage(exception), exception);
        }
    }

    public Optional<WechatReport> find(String id) {
        cleanupExpired();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        WechatReport report = reports.get(id.strip());
        if (report != null && Files.exists(report.path())) {
            return Optional.of(report);
        }

        Path path = normalize(properties.getStorageDir()).resolve(id.strip() + ".html");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(new WechatReport(id.strip(), "微信回复报告", publicUrl(id.strip()), path, Instant.EPOCH, Instant.MAX));
    }

    public void cleanupExpired() {
        Instant now = Instant.now();
        reports.values().removeIf(report -> {
            if (report.expiresAt().isAfter(now)) {
                return false;
            }
            try {
                Files.deleteIfExists(report.path());
            } catch (IOException ignored) {
            }
            return true;
        });
    }

    private String renderHtml(String id, String title, String text, Instant createdAt, Instant expiresAt) {
        String summary = summary(text);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root { color-scheme: light dark; --bg:#f6f7f9; --card:#ffffff; --text:#1f2937; --muted:#6b7280; --line:#e5e7eb; --accent:#2563eb; --accent-soft:#dbeafe; }
                    @media (prefers-color-scheme: dark) { :root { --bg:#111827; --card:#1f2937; --text:#f9fafb; --muted:#9ca3af; --line:#374151; --accent:#60a5fa; --accent-soft:#1e3a8a; } }
                    * { box-sizing:border-box; }
                    body { margin:0; background:var(--bg); color:var(--text); font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif; }
                    main { width:min(920px,100%%); margin:0 auto; padding:20px 14px 48px; }
                    header { padding:18px 2px 12px; }
                    h1 { margin:0 0 8px; font-size:26px; line-height:1.25; letter-spacing:0; }
                    .meta { color:var(--muted); font-size:14px; }
                    .card { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:16px; margin:14px 0; box-shadow:0 1px 2px rgba(15,23,42,.05); }
                    .summary { border-left:4px solid var(--accent); }
                    .section-title { margin:0 0 10px; font-size:18px; }
                    .content { white-space:pre-wrap; word-break:break-word; }
                    .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:10px; }
                    .item { background:var(--bg); border:1px solid var(--line); border-radius:8px; padding:12px; }
                    .item-title { font-weight:700; margin-bottom:6px; }
                    .item-desc { color:var(--muted); }
                    .badge { display:inline-block; padding:2px 8px; border-radius:999px; background:var(--accent-soft); color:var(--accent); font-size:13px; margin-right:6px; }
                    footer { color:var(--muted); font-size:14px; text-align:center; margin-top:24px; }
                    .top { position:fixed; right:14px; bottom:14px; width:44px; height:44px; border-radius:50%%; border:1px solid var(--line); background:var(--card); color:var(--text); text-decoration:none; display:flex; align-items:center; justify-content:center; box-shadow:0 4px 16px rgba(0,0,0,.12); }
                  </style>
                </head>
                <body id="top">
                  <main>
                    <header>
                      <h1>%s</h1>
                      <div class="meta"><span class="badge">OpenClaw</span>生成时间：%s　过期时间：%s</div>
                    </header>
                    <section class="card summary">
                      <h2 class="section-title">核心摘要</h2>
                      <div class="content">%s</div>
                    </section>
                    %s
                    <footer>报告编号：%s。页面可分享给朋友；如需长期保存，可复制内容或生成 PDF。</footer>
                  </main>
                  <a class="top" href="#top" aria-label="返回顶部">↑</a>
                </body>
                </html>
                """.formatted(
                escape(title),
                escape(title),
                TIME_FORMAT.format(createdAt),
                TIME_FORMAT.format(expiresAt),
                escape(summary),
                renderBody(text),
                escape(id));
    }

    private String renderBody(String text) {
        List<String> items = listItems(text);
        if (items.size() > properties.getItemCountThreshold()) {
            StringBuilder grid = new StringBuilder("""
                    <section class="card">
                      <h2 class="section-title">详细内容</h2>
                      <div class="grid">
                    """);
            for (String item : items) {
                grid.append(renderItemCard(item));
            }
            grid.append("</div></section>");
            return grid.toString();
        }
        return """
                <section class="card">
                  <h2 class="section-title">完整内容</h2>
                  <div class="content">%s</div>
                </section>
                """.formatted(escape(text));
    }

    private String summary(String text) {
        String value = text == null ? "" : text.strip();
        List<String> lines = value.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.matches("^[-*+]?\\s*$"))
                .limit(3)
                .toList();
        String summary = String.join("\n", lines);
        if (summary.length() > 260) {
            return summary.substring(0, 257) + "...";
        }
        return summary.isBlank() ? "完整内容请查看下方详情。" : summary;
    }

    public String summaryForWechat(String text) {
        return summary(text);
    }

    public String inferTitle(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isBlank()) {
            return "微信回复报告";
        }
        String first = value.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("微信回复报告");
        first = first.replaceAll("^#+\\s*", "")
                .replaceAll("^[\\[【].*?[\\]】]\\s*", "")
                .replaceAll("[:：]\\s*$", "")
                .strip();
        if (first.length() > 28) {
            first = first.substring(0, 28) + "...";
        }
        return first.isBlank() ? "微信回复报告" : first;
    }

    private boolean looksLikeTable(String text) {
        long tableLines = text.lines().filter(line -> line.chars().filter(ch -> ch == '|').count() >= 2).count();
        return tableLines >= 3;
    }

    private boolean looksLikeFormalReport(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return value.contains("报告")
                || value.contains("会议纪要")
                || value.contains("汇总")
                || value.contains("完整内容")
                || value.contains("详细分析");
    }

    private int listItemCount(String text) {
        return listItems(text).size();
    }

    private List<String> listItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        String pendingHeading = "";
        int lastItemIndex = -1;
        for (String line : text.split("\\R")) {
            String value = line.strip();
            if (value.isBlank()) {
                continue;
            }
            String heading = sectionHeading(value);
            if (!heading.isBlank()) {
                pendingHeading = heading;
                lastItemIndex = -1;
                continue;
            }
            if (value.matches("^([-•*]\\s+|\\d+[.、]\\s*|[一二三四五六七八九十]+[、.]\\s*).+")) {
                String item = value.replaceFirst("^([-•*]\\s+|\\d+[.、]\\s*|[一二三四五六七八九十]+[、.]\\s*)", "");
                item = cleanMarkdownListItem(item);
                if (!item.isBlank() && !isSectionHeading(item) && !isAdviceHeading(item)) {
                    if (!pendingHeading.isBlank() && shouldAttachHeading(pendingHeading, item)) {
                        item = pendingHeading + "：" + item;
                    }
                    items.add(item);
                    lastItemIndex = items.size() - 1;
                } else {
                    lastItemIndex = -1;
                }
                continue;
            }
            String continuation = cleanMarkdownListItem(value);
            if (lastItemIndex >= 0 && shouldAppendContinuation(continuation)) {
                items.set(lastItemIndex, appendDetail(items.get(lastItemIndex), continuation));
            }
        }
        return items;
    }

    private String cleanMarkdownListItem(String value) {
        String item = value == null ? "" : value.strip();
        item = item.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("^#+\\s*", "")
                .replaceAll("^[-•*]+\\s*", "")
                .replaceAll("^[\\p{So}\\p{Cn}\\s]+", "")
                .replaceAll("\\*+", "")
                .replaceAll("\\s+", " ")
                .strip();
        return item;
    }

    private boolean isSectionHeading(String value) {
        String item = value == null ? "" : value.strip();
        if (!item.endsWith("：") && !item.endsWith(":")) {
            return false;
        }
        String title = item.substring(0, item.length() - 1).strip();
        return !title.contains("，")
                && !title.contains(",")
                && !title.contains("。")
                && !title.contains("；")
                && !title.contains(";")
                && title.length() <= 16;
    }

    private String sectionHeading(String value) {
        String item = cleanMarkdownListItem(value);
        if (item.isBlank()) {
            return "";
        }
        if (item.startsWith("推荐") || item.contains("建议") || item.contains("贴士") || item.contains("小贴士")) {
            return "";
        }
        if (item.endsWith("：") || item.endsWith(":")) {
            item = item.substring(0, item.length() - 1).strip();
        }
        if (item.length() > 18) {
            return "";
        }
        return item.matches(".*(大菜|热炒|点心|小吃|主食|汤羹|甜品|水产|特色|推荐|名菜|菜品).*")
                ? item
                : "";
    }

    private boolean shouldAttachHeading(String heading, String item) {
        if (heading == null || heading.isBlank() || item == null || item.isBlank()) {
            return false;
        }
        return !item.startsWith(heading + "：") && !item.startsWith(heading + ":");
    }

    private boolean shouldAppendContinuation(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !isSectionHeading(value)
                && sectionHeading(value).isBlank()
                && !isAdviceHeading(value)
                && !value.matches("^[-•*+]+$");
    }

    private String appendDetail(String item, String detail) {
        String value = item == null ? "" : item.strip();
        String extra = detail == null ? "" : detail.strip();
        if (extra.isBlank() || value.contains(extra)) {
            return value;
        }
        if (value.endsWith("。") || value.endsWith("！") || value.endsWith("？")
                || value.endsWith(".") || value.endsWith("!") || value.endsWith("?")) {
            return value + extra;
        }
        return value + "。" + extra;
    }

    private String renderItemCard(String item) {
        ItemText itemText = splitItemText(item);
        if (itemText.description().isBlank()) {
            return "<div class=\"item\"><div class=\"item-title\">%s</div></div>"
                    .formatted(escape(itemText.title()));
        }
        return "<div class=\"item\"><div class=\"item-title\">%s</div><div class=\"item-desc\">%s</div></div>"
                .formatted(escape(itemText.title()), escape(itemText.description()));
    }

    private ItemText splitItemText(String item) {
        String value = item == null ? "" : item.strip();
        int firstColon = firstColonIndex(value, 0);
        if (firstColon < 0) {
            return new ItemText(value, "");
        }
        int secondColon = firstColonIndex(value, firstColon + 1);
        int splitIndex = secondColon >= 0 ? secondColon : firstColon;
        String title = value.substring(0, splitIndex).strip();
        String description = value.substring(splitIndex + 1).strip();
        return new ItemText(title, description);
    }

    private int firstColonIndex(String value, int fromIndex) {
        int cn = value.indexOf('：', fromIndex);
        int en = value.indexOf(':', fromIndex);
        if (cn < 0) {
            return en;
        }
        if (en < 0) {
            return cn;
        }
        return Math.min(cn, en);
    }

    private record ItemText(String title, String description) {
    }

    private boolean isAdviceHeading(String value) {
        String item = cleanMarkdownListItem(value);
        if (item.endsWith("：") || item.endsWith(":")) {
            item = item.substring(0, item.length() - 1).strip();
        }
        return item.length() <= 18
                && (item.contains("建议")
                || item.contains("贴士")
                || item.contains("小贴士")
                || item.contains("寻味"));
    }

    private boolean containsSensitiveHint(String text) {
        String value = text == null ? "" : text;
        return value.contains("邮件内容：")
                || value.contains("最近邮件：")
                || value.contains("邮件搜索结果：")
                || value.contains("身份证")
                || value.contains("银行卡")
                || value.contains("授权码")
                || value.contains("密码：")
                || value.contains("验证码");
    }

    private String publicUrl(String id) {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            if (localServerPort > 0) {
                return "http://127.0.0.1:" + localServerPort + "/r/" + id;
            }
            return "http://127.0.0.1/r/" + id;
        }
        return base.replaceAll("/+$", "") + "/r/" + id;
    }

    private String newId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        } while (reports.containsKey(id));
        return id;
    }

    private Path normalize(Path path) {
        Path value = path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path);
        return value.normalize().toAbsolutePath();
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

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
