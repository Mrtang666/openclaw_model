package com.example.file;

import com.example.intent.BotIntent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the next requested operation for each user's received file. */
public class FileTaskService {

    private final Map<String, FileDocumentService.Document> pending = new ConcurrentHashMap<>();
    private final Map<String, FileDocumentService.Document> latest = new ConcurrentHashMap<>();
    private final Map<String, String> requestedOperations = new ConcurrentHashMap<>();

    public void start(String userId, FileDocumentService.Document document) {
        pending.put(userId, document);
        latest.put(userId, document);
        requestedOperations.remove(userId);
    }

    public boolean hasPending(String userId) {
        return userId != null && pending.containsKey(userId);
    }

    public boolean shouldContinue(String userId, BotIntent intent) {
        return shouldContinue(userId, intent, "");
    }

    public boolean shouldContinue(String userId, BotIntent intent, String request) {
        if (userId == null) return false;
        if (hasPending(userId)) {
            return intent == BotIntent.CHAT
                    || (intent == BotIntent.FILE_GENERATION
                        && (isFileOperationRequest(request)
                            || request != null && request.matches(".*(生成|导出|转换|pdf|word|docx|excel|xlsx|csv|json|markdown|md|txt).*")))
                    || isFormatOnly(request);
        }
        return hasLatest(userId) && (isFileOperationRequest(request)
                || hasRequestedOperation(userId) && isFormatOnly(request));
    }

    public boolean hasLatest(String userId) {
        return userId != null && latest.containsKey(userId);
    }

    public boolean hasRequestedOperation(String userId) {
        return userId != null && requestedOperations.containsKey(userId);
    }

    public FileDocumentService.Document getLatest(String userId) {
        return userId == null ? null : latest.get(userId);
    }

    public void rememberGenerated(String userId, FileDocumentService.Document document) {
        if (userId == null || document == null) return;
        latest.put(userId, document);
        pending.remove(userId);
        requestedOperations.remove(userId);
    }

    public void cancel(String userId) {
        if (userId != null) {
            pending.remove(userId);
            requestedOperations.remove(userId);
        }
    }

    public Result accept(String userId, String request) {
        if (userId == null) return Result.notHandled();
        FileDocumentService.Document document = pending.get(userId);
        if (document == null) document = latest.get(userId);
        if (document == null) return Result.notHandled();
        String value = request == null ? "" : request.trim();
        String normalized = value.replaceAll("[\\s，。！？、,.?；;：:]+", "");

        if (isCancellation(normalized)) {
            pending.remove(userId);
            return Result.cancel("好的，已取消当前文件任务。你可以继续发送其他问题。");
        }

        if (normalized.contains("总结") || normalized.contains("摘要") || normalized.contains("概括")) {
            pending.remove(userId);
            return Result.analyze("请总结以下文件内容，提炼主要结论、关键数据和待办事项：\n" + document.getText());
        }
        if (normalized.contains("提取") && (normalized.contains("关键") || normalized.contains("信息") || normalized.contains("要点"))) {
            pending.remove(userId);
            return Result.analyze("请从以下文件内容中提取关键信息，要求简洁、分点输出。"
                    + "如果是合同，请重点提取合同主体、标的、金额、期限、权利义务、违约责任、争议解决和需要注意的风险点：\n"
                    + document.getText());
        }
        if (normalized.contains("读取") || normalized.contains("原文") || normalized.contains("全文")) {
            pending.remove(userId);
            return Result.extract(document.getText());
        }

        String format = detectFormat(normalized);
        String previousRequest = requestedOperations.get(userId);
        if (isModificationRequest(normalized) || previousRequest != null) {
            String operation = previousRequest == null || previousRequest.isBlank()
                    ? value : previousRequest + "\n补充信息：" + value;
            if (format == null) {
                requestedOperations.put(userId, operation);
                return Result.ask("可以修改。请告诉我修改后要输出的格式，例如 PDF、Word、Excel、Markdown 或 TXT。");
            }
            pending.remove(userId);
            requestedOperations.remove(userId);
            return Result.generate(format, buildModificationPrompt(document, operation, format));
        }
        if (normalized.contains("生成") || normalized.contains("导出") || normalized.contains("转换") || format != null) {
            if (format == null) return Result.ask("请告诉我要生成什么格式，例如 Word、PDF、Excel、CSV、JSON 或 TXT。");
            pending.remove(userId);
            return Result.generate(format,
                    "请根据以下文件内容生成一份" + format.toUpperCase() + "文件，保持信息准确、结构清晰：\n" + document.getText());
        }

        pending.remove(userId);
        return Result.analyze("请根据文件内容回答用户问题。文件内容如下：\n" + document.getText() + "\n用户问题：" + value);
    }

    public boolean isFileOperationRequest(String request) {
        String value = request == null ? "" : request.trim().toLowerCase();
        if (value.isBlank()) return false;
        return value.matches(".*(上面的文件|这个文件|刚才的文件|该文件|生成的文件|上传的文件|修改|改写|重写|整理|优化|补充|添加|删除|替换|调整|格式化|转换|导出|提取|总结).*");
    }

    private boolean isFormatOnly(String request) {
        String value = request == null ? "" : request.trim().toLowerCase();
        return value.matches("^(pdf|word|docx|excel|xlsx|csv|json|markdown|md|txt)$");
    }

    private boolean isModificationRequest(String text) {
        return text.matches(".*(修改|改写|重写|整理|优化|补充|添加|删除|替换|调整|改成|增加|完善|修订|纠正).*");
    }

    private String buildModificationPrompt(FileDocumentService.Document document,
                                            String operation, String format) {
        return "请基于下面的原文件内容完成用户的修改要求，并输出修改后的完整内容，"
                + "用于生成 " + format.toUpperCase() + " 文件。不要只描述修改方案，不要省略未要求修改的有效内容。"
                + "如果原文件内容杂乱，请重新组织标题、段落和列表；如果用户要求补充信息，请补充准确、连贯的正文。"
                + "只输出最终的简体中文正文，不要输出代码围栏、随机外语、重复片段或“1年份”“第名名”等占位符；"
                + "事实不确定时不要编造具体年份、排名或人物信息。"
                + "用户修改要求：\n" + operation
                + "\n原文件名：" + document.getFileName()
                + "\n原文件内容：\n" + document.getText();
    }

    private boolean isCancellation(String text) {
        return text.equals("取消")
                || text.equals("取消文件任务")
                || text.equals("结束文件任务")
                || text.equals("停止文件任务")
                || text.equals("算了")
                || text.equals("不用了")
                || text.equals("不需要了")
                || text.equals("没有了")
                || text.equals("没有了谢谢")
                || text.equals("谢谢不用了")
                || text.equals("不用生成了")
                || text.equals("不用处理了");
    }

    public String buildMenu(FileDocumentService.Document document) {
        return "已收到文件《" + document.getFileName() + "》（" + document.getType().name().toLowerCase()
                + "）。你希望我做什么？\n1. 总结内容\n2. 提取关键信息\n3. 回答文件相关问题\n4. 生成或转换成其他格式";
    }

    private String detectFormat(String text) {
        if (text.contains("pdf")) return "pdf";
        if (text.contains("excel") || text.contains("xlsx") || text.contains("表格")) return "xlsx";
        if (text.contains("csv")) return "csv";
        if (text.contains("json")) return "json";
        if (text.contains("markdown") || text.contains("md")) return "md";
        if (text.contains("txt") || text.contains("文本")) return "txt";
        if (text.contains("word") || text.contains("docx") || text.contains("文档")) return "docx";
        return null;
    }

    public enum Action { NONE, ASK, CANCEL, ANALYZE, EXTRACT, GENERATE }

    public static class Result {
        private final Action action;
        private final String message;
        private final String format;

        private Result(Action action, String message, String format) {
            this.action = action;
            this.message = message;
            this.format = format;
        }

        public static Result notHandled() { return new Result(Action.NONE, "", null); }
        public static Result ask(String message) { return new Result(Action.ASK, message, null); }
        public static Result cancel(String message) { return new Result(Action.CANCEL, message, null); }
        public static Result analyze(String prompt) { return new Result(Action.ANALYZE, prompt, null); }
        public static Result extract(String content) { return new Result(Action.EXTRACT, content, null); }
        public static Result generate(String format, String prompt) { return new Result(Action.GENERATE, prompt, format); }

        public Action getAction() { return action; }
        public String getMessage() { return message; }
        public String getFormat() { return format; }
    }
}
