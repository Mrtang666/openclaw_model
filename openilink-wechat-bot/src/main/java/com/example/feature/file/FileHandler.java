package com.example.feature.file;

import com.example.LocalLLMService;
import com.example.adapter.wechat.WechatMessageSender;
import com.example.application.ReplyOrchestrator;
import com.example.file.FileDocumentService;
import com.example.file.FileMediaDownloadService;
import com.example.file.FileTaskService;
import com.example.intent.BotIntent;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles file reception and the follow-up analyze/generate task lifecycle. */
public class FileHandler {

    private static final Logger log = LoggerFactory.getLogger(FileHandler.class);
    private static final Pattern REPEATED_FRAGMENT = Pattern.compile("(.{2,20})\\1{3,}");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^\\s*```[A-Za-z0-9_-]*\\s*|\\s*```\\s*$");
    private static final Pattern LENGTH_REQUEST = Pattern.compile(
            "(?:约|大约|大概|不少于|至少)?\\s*([0-9]{2,6}|[一二两三四五六七八九十百千万]+)\\s*(?:个)?(?:字|字符)");
    private final ConcurrentHashMap<String, String> pendingStandaloneRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestStandaloneRequests = new ConcurrentHashMap<>();

    private final FileMediaDownloadService downloads;
    private final FileDocumentService documents;
    private final FileTaskService tasks;
    private final LocalLLMService llm;
    private final WechatMessageSender sender;
    private final ReplyOrchestrator replies;

    public FileHandler(FileMediaDownloadService downloads,
                       FileDocumentService documents,
                       FileTaskService tasks,
                       LocalLLMService llm,
                       WechatMessageSender sender,
                       ReplyOrchestrator replies) {
        this.downloads = downloads;
        this.documents = documents;
        this.tasks = tasks;
        this.llm = llm;
        this.sender = sender;
        this.replies = replies;
    }

    public void receive(ILinkClient client, MessageItem item, String userId) {
        FileItem file = item.getFile_item();
        if (file == null) return;
        log.info("收到用户 [{}] 的文件", userId);
        try {
            byte[] bytes = downloads.download(client, item);
            FileDocumentService.Document document = documents.readAndSave(
                    userId, file.getFile_name(), bytes);
            tasks.start(userId, document);
            replies.reply(client, userId, tasks.buildMenu(document));
        } catch (Exception e) {
            log.warn("文件接收或解析失败: user={}, file={}, error={}",
                    userId, file.getFile_name(), e.getMessage());
            replies.reply(client, userId, downloads.userMessage(e));
        }
    }

    public boolean handleTask(ILinkClient client, String userId, FileTaskService.Result result) {
        if (result == null || result.getAction() == FileTaskService.Action.NONE) return false;
        try {
            switch (result.getAction()) {
                case ASK:
                case EXTRACT:
                case CANCEL:
                    replies.reply(client, userId, result.getMessage());
                    return true;
                case ANALYZE:
                    replies.reply(client, userId, llm.chat(userId, result.getMessage()));
                    return true;
                case GENERATE:
                    generateFile(client, userId, result);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("文件任务处理失败: {}", e.getMessage());
            replies.reply(client, userId, "文件任务处理失败：" + e.getMessage());
            return true;
        }
    }

    public boolean shouldContinue(String userId, BotIntent intent, String text) {
        return tasks.shouldContinue(userId, intent, text);
    }

    public boolean hasPending(String userId) {
        return tasks.hasPending(userId);
    }

    public boolean hasLatest(String userId) {
        return tasks.hasLatest(userId);
    }

    public boolean hasRequestedOperation(String userId) {
        return tasks.hasRequestedOperation(userId);
    }

    public boolean hasPendingGeneration(String userId) {
        return userId != null && pendingStandaloneRequests.containsKey(userId);
    }

    /** Continues a standalone generation request when the user replies with only a format. */
    public boolean continuePendingGeneration(ILinkClient client, String userId, String text) {
        if (!hasPendingGeneration(userId)) return false;
        String format = detectFormat(text);
        if (format == null) return false;
        String original = pendingStandaloneRequests.remove(userId);
        log.info("继续文件生成任务: user={}, format={}, request={}", userId, format, original);
        generateStandalone(client, userId, original + "，输出格式为" + format);
        return true;
    }

    public void cancelPendingGeneration(String userId) {
        if (userId != null) pendingStandaloneRequests.remove(userId);
    }

    public boolean isFormatOnly(String text) {
        String value = text == null ? "" : text.trim();
        return value.matches("(?i)^(pdf|word|docx|excel|xlsx|csv|json|markdown|md|txt)$");
    }

    /** Allows a format-only follow-up to regenerate the latest standalone document. */
    public boolean continueLatestGeneration(ILinkClient client, String userId, String text) {
        if (!isFormatOnly(text) || userId == null) return false;
        String original = latestStandaloneRequests.get(userId);
        if (original == null || original.isBlank()) return false;
        String format = detectFormat(text);
        log.info("按最近文件生成请求重新生成: user={}, format={}, request={}", userId, format, original);
        generateStandalone(client, userId, original + "，输出格式为" + format);
        return true;
    }

    public boolean isFileOperationRequest(String text) {
        return tasks.isFileOperationRequest(text);
    }

    public void cancel(String userId) {
        tasks.cancel(userId);
    }

    public FileTaskService.Result accept(String userId, String text) {
        return tasks.accept(userId, text);
    }

    public void generateStandalone(ILinkClient client, String userId, String request) {
        String format = detectFormat(request);
        if (format == null) {
            pendingStandaloneRequests.put(userId, request);
            replies.reply(client, userId,
                    "可以，我能按你的要求生成文件。请告诉我要生成什么格式：Word、PDF、Excel、CSV、JSON、Markdown 或 TXT。");
            return;
        }
        pendingStandaloneRequests.remove(userId);
        latestStandaloneRequests.put(userId, request);

        replies.reply(client, userId,
                "好的，正在按你的要求生成 " + displayFormat(format) + " 文件，请稍候...");
        try {
            LengthRequirement length = parseLengthRequirement(request);
            log.info("文件生成字数约束: user={}, request={}, required={}",
                    userId, request, length == null ? "none" : length.describe());
            String content = generateDocumentContent(userId,
                    buildStandalonePrompt(request, format, length), length);
            String fileName = buildFileName(request, format);
            FileDocumentService.Document generated = documents.generateAndSave(
                    userId, format, fileName, content);
            sender.sendFile(client, userId, Files.readAllBytes(generated.getPath()),
                    generated.getFileName(), null);
            tasks.rememberGenerated(userId, generated);
            replies.reply(client, userId, "文件已生成并发送给你了：" + fileName);
        } catch (Exception e) {
            log.warn("按需求生成文件失败: request={}, format={}, error={}", request, format, e.getMessage());
            replies.reply(client, userId, "文件生成失败：" + e.getMessage());
        }
    }

    private void generateFile(ILinkClient client, String userId, FileTaskService.Result result) throws Exception {
        LengthRequirement length = parseLengthRequirement(result.getMessage());
        String content = generateDocumentContent(userId,
                appendLengthInstruction(result.getMessage(), length), length);
        FileDocumentService.Document source = tasks.getLatest(userId);
        String fileName = buildModifiedFileName(source, result.getFormat());
        FileDocumentService.Document generated = documents.generateAndSave(
                userId, result.getFormat(), fileName, content);
        sender.sendFile(client, userId, Files.readAllBytes(generated.getPath()),
                generated.getFileName(), null);
        tasks.rememberGenerated(userId, generated);
        replies.reply(client, userId, "已按要求修改文件并发送给你了：" + generated.getFileName());
    }

    private String buildStandalonePrompt(String request, String format, LengthRequirement length) {
        String typeHint;
        switch (format) {
            case "xlsx":
                typeHint = "请输出适合写入 Excel 的表格内容。尽量使用制表符分隔列，每一行代表一条记录；如果需要多张表，请用清晰的小标题分隔。";
                break;
            case "csv":
                typeHint = "请输出标准 CSV 内容，第一行是表头，字段用英文逗号分隔，必要时用双引号包裹。";
                break;
            case "json":
                typeHint = "请输出合法 JSON，不要使用 Markdown 代码块。";
                break;
            case "md":
                typeHint = "请输出 Markdown 文档，使用标题、列表和表格组织内容。";
                break;
            default:
                typeHint = "请输出一份结构清晰、可直接写入文件的正文内容。";
                break;
        }
        return "请根据用户要求生成一份 " + format.toUpperCase() + " 文件内容。\n"
                + "用户要求：" + request + "\n" + typeHint
                + "\n要求信息完整、表达专业、不要在开头解释你将如何生成文件。"
                + "只输出最终文件正文，使用简体中文。不要输出 Markdown 代码围栏，不要使用“1年份”“第名名”等占位符，"
                + "不要重复句子或段落；事实不确定时用准确的概括表达，不要编造具体年份、排名或球员信息。"
                + lengthInstruction(length);
    }

    private String generateDocumentContent(String userId, String prompt, LengthRequirement length) {
        String content = normalizeGeneratedContent(llm.chatForDocument(userId, prompt));
        if (!looksMalformed(content) && isLengthValid(content, length)) return content;

        log.warn("检测到文件生成内容不合格，发起一次内容重写: user={}, chineseChars={}, required={}",
                userId, countChineseCharacters(content), length == null ? "none" : length.describe());
        String repairPrompt = "请重写下面的文件正文，修复其中的乱码、随机外语、占位符、重复片段和不完整句子。"
                + "只输出修复后的完整简体中文正文，保留原主题和用户要求，不要解释修复过程。"
                + "禁止出现“1年份”“第名名”“s年份”等占位或重复文本。"
                + lengthInstruction(length)
                + "\n原始草稿：\n" + content;
        String repaired = normalizeGeneratedContent(llm.chatForDocument(userId, repairPrompt));
        if (looksMalformed(repaired) || !isLengthValid(repaired, length)) {
            throw new IllegalStateException("大模型返回的文件内容质量或字数不符合要求，未发送文件");
        }
        return repaired;
    }

    private String appendLengthInstruction(String prompt, LengthRequirement length) {
        return prompt + lengthInstruction(length);
    }

    private String lengthInstruction(LengthRequirement length) {
        if (length == null) return "";
        return "正文必须控制在 " + length.min + " 至 " + length.max + " 个汉字之间，标题不计入字数；"
                + "请先规划结构，再输出完整正文，不要为了凑字数重复句子。";
    }

    private LengthRequirement parseLengthRequirement(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = LENGTH_REQUEST.matcher(text);
        if (!matcher.find()) return null;
        int target;
        String value = matcher.group(1);
        try {
            target = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            target = parseChineseNumber(value);
        }
        if (target < 100) return null;
        return new LengthRequirement(target,
                Math.max(100, (int) Math.floor(target * 0.9)),
                (int) Math.ceil(target * 1.1));
    }

    private int parseChineseNumber(String value) {
        int total = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            int digit = chineseDigit(current);
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(current);
            if (unit < 10_000) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            } else {
                section += number;
                total += section * unit;
                section = 0;
                number = 0;
            }
        }
        return total + section + number;
    }

    private int chineseDigit(char value) {
        String digits = "零一二两三四五六七八九";
        return digits.indexOf(value);
    }

    private int chineseUnit(char value) {
        if (value == '十') return 10;
        if (value == '百') return 100;
        if (value == '千') return 1000;
        if (value == '万') return 10_000;
        return 0;
    }

    private boolean isLengthValid(String content, LengthRequirement length) {
        if (length == null) return true;
        int count = countChineseCharacters(content);
        return count >= length.min && count <= length.max;
    }

    private int countChineseCharacters(String content) {
        if (content == null) return 0;
        int count = 0;
        for (int i = 0; i < content.length();) {
            int codePoint = content.codePointAt(i);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) count++;
            i += Character.charCount(codePoint);
        }
        return count;
    }

    private String normalizeGeneratedContent(String content) {
        if (content == null) return "";
        return CODE_FENCE.matcher(content)
                .replaceAll("")
                .replace('\u0000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private boolean looksMalformed(String content) {
        if (content == null || content.isBlank()) return true;
        if (content.indexOf('\uFFFD') >= 0) return true;
        if (content.contains("1年份") || content.contains("第名名")
                || content.contains("s年份") || content.contains("lógica")) {
            return true;
        }
        String compact = content.replaceAll("\\s+", "");
        return compact.length() > 500 && REPEATED_FRAGMENT.matcher(compact).find();
    }

    private static class LengthRequirement {
        private final int target;
        private final int min;
        private final int max;

        private LengthRequirement(int target, int min, int max) {
            this.target = target;
            this.min = min;
            this.max = max;
        }

        private String describe() {
            return target + "(" + min + "-" + max + ")";
        }
    }

    private String detectFormat(String text) {
        String value = text == null ? "" : text.toLowerCase();
        if (value.contains("pdf")) return "pdf";
        if (value.contains("xlsx") || value.contains("excel") || value.contains("表格")) return "xlsx";
        if (value.contains("csv")) return "csv";
        if (value.contains("json")) return "json";
        if (value.contains("markdown") || value.matches("(?s).*\\bmd\\b.*")) return "md";
        if (value.contains("txt") || value.contains("文本")) return "txt";
        if (value.contains("docx") || value.contains("word") || value.contains("文档")) return "docx";
        return null;
    }

    private String displayFormat(String format) {
        return "docx".equals(format) ? "Word"
                : "xlsx".equals(format) ? "Excel"
                : "md".equals(format) ? "Markdown" : format.toUpperCase();
    }

    private String buildFileName(String request, String format) {
        String base = request == null ? "" : request
                .replaceAll("(?i)docx|word|pdf|xlsx|excel|csv|json|markdown|md|txt", "")
                .replaceAll("(帮我|给我|请|生成|创建|新建|制作|做一份|做一个|写一份|写一个|整理成|导出|输出|转成|转换成|文件|文档|表格)", "")
                .replaceAll("[\\\\/:*?\"<>|\\s，。！？、,.?；;：:]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) base = "generated_file";
        if (base.length() > 24) base = base.substring(0, 24).replaceAll("_+$", "");
        return base + "_" + System.currentTimeMillis() + "." + format;
    }

    private String buildModifiedFileName(FileDocumentService.Document source, String format) {
        String base = source == null ? "modified_file" : source.getFileName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5_-]", "_");
        if (base.isBlank()) base = "modified_file";
        return "修改后_" + base + "_" + System.currentTimeMillis() + "." + format;
    }
}
