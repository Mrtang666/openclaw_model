package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.service.KnowledgeManageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Function Calling 知识库管理工具。
 *
 * <p>支持列表、查看、筛选、修改标题、修改标签、删除、批量删除和重新向量化入口。
 * 删除、批量删除、重新向量化属于风险操作，必须二次确认。</p>
 */
@Component
public class KnowledgeManageWechatTool implements WechatTool {

    private final KnowledgeManageService manageService;
    private final Map<String, PendingOperation> pendingOperations = new ConcurrentHashMap<>();

    public KnowledgeManageWechatTool(KnowledgeManageService manageService) {
        this.manageService = manageService;
    }

    @Override
    public String name() {
        return "knowledge_manage";
    }

    @Override
    public String description() {
        return "管理用户个人知识库资料：列出、查看、筛选、修改标题、修改标签、删除、批量删除和重新向量化。风险操作需要二次确认。";
    }

    @Override
    public List<String> arguments() {
        return List.of("operation", "keyword", "tags", "source_type", "document_id", "document_ids", "title", "confirm");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "operation",
                        "管理操作",
                        List.of("list", "detail", "delete", "batch_delete", "update_title", "update_tags", "reindex"),
                        "list"),
                WechatToolParameter.optionalString("keyword", "按标题、标签或来源链接筛选", "项目"),
                WechatToolParameter.optionalString("tags", "按标签筛选或更新标签", "Qdrant,Java"),
                WechatToolParameter.optionalString("source_type", "按来源类型筛选，例如 web、file、text", "web"),
                WechatToolParameter.optionalString("document_id", "知识文档 ID，detail/delete/update/reindex 时需要", "1"),
                WechatToolParameter.optionalString("document_ids", "批量操作的文档 ID，逗号分隔", "1,2,3"),
                WechatToolParameter.optionalString("title", "新标题，update_title 时需要", "Qdrant Java 接入指南"),
                WechatToolParameter.optionalBoolean("confirm", "是否确认执行风险操作", false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "产品化管理用户个人知识库资料。",
                List.of("删除、批量删除、重新向量化必须先确认；无法恢复原文时不能假装重新向量化成功。"),
                List.of("detail/delete/update/reindex 需要 document_id；batch_delete 需要 document_ids 或筛选条件。"),
                List.of("返回资料列表、详情、更新结果、确认提示或执行结果。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String operation = defaultText(argument(request, "operation"), "list").toLowerCase(java.util.Locale.ROOT);
        if (isConfirmation(request)) {
            PendingOperation pending = pendingOperations.remove(request.sessionKey());
            if (pending != null) {
                return executeConfirmed(request.sessionKey(), pending);
            }
        }
        return switch (operation) {
            case "detail" -> detail(request);
            case "delete" -> confirmDelete(request);
            case "batch_delete" -> confirmBatchDelete(request);
            case "update_title" -> updateTitle(request);
            case "update_tags" -> updateTags(request);
            case "reindex" -> confirmReindex(request);
            default -> list(request);
        };
    }

    private WechatReply list(WechatToolRequest request) {
        return WechatReply.text(formatList(manageService.list(
                request.sessionKey(),
                argument(request, "keyword"),
                argument(request, "tags"),
                argument(request, "source_type"),
                parseInt(argument(request, "limit"), 10))));
    }

    private WechatReply detail(WechatToolRequest request) {
        long documentId = parseLong(argument(request, "document_id"));
        if (documentId <= 0) {
            return WechatReply.text("请提供要查看的知识文档 document_id。");
        }
        return manageService.detail(request.sessionKey(), documentId)
                .map(document -> WechatReply.text(formatDocument(document)))
                .orElseGet(() -> WechatReply.text("没有找到知识文档：document_id=" + documentId));
    }

    private WechatReply updateTitle(WechatToolRequest request) {
        long documentId = parseLong(argument(request, "document_id"));
        String title = argument(request, "title");
        if (documentId <= 0 || title.isBlank()) {
            return WechatReply.text("修改标题需要同时提供 document_id 和 title。");
        }
        return WechatReply.text(manageService.updateTitle(request.sessionKey(), documentId, title)
                ? "标题已更新：document_id=" + documentId
                : "标题更新失败，没有找到对应知识文档：document_id=" + documentId);
    }

    private WechatReply updateTags(WechatToolRequest request) {
        long documentId = parseLong(argument(request, "document_id"));
        String tags = argument(request, "tags");
        if (documentId <= 0 || tags.isBlank()) {
            return WechatReply.text("修改标签需要同时提供 document_id 和 tags。");
        }
        return WechatReply.text(manageService.updateTags(request.sessionKey(), documentId, tags)
                ? "标签已更新：document_id=" + documentId
                : "标签更新失败，没有找到对应知识文档：document_id=" + documentId);
    }

    private WechatReply confirmDelete(WechatToolRequest request) {
        long documentId = parseLong(argument(request, "document_id"));
        if (documentId <= 0) {
            return WechatReply.text("请提供要删除的知识文档 document_id。");
        }
        PendingOperation pending = PendingOperation.delete(documentId);
        if (request.booleanArgument("confirm")) {
            return executeConfirmed(request.sessionKey(), pending);
        }
        pendingOperations.put(request.sessionKey(), pending);
        return WechatReply.text("请确认是否删除知识文档：document_id=" + documentId + "。确认后回复“确认”。");
    }

    private WechatReply confirmBatchDelete(WechatToolRequest request) {
        String documentIds = argument(request, "document_ids");
        String keyword = argument(request, "keyword");
        String tags = argument(request, "tags");
        String sourceType = argument(request, "source_type");
        if (documentIds.isBlank() && keyword.isBlank() && tags.isBlank() && sourceType.isBlank()) {
            return WechatReply.text("批量删除需要提供 document_ids，或提供 keyword/tags/source_type 筛选条件。");
        }
        PendingOperation pending = PendingOperation.batchDelete(documentIds, keyword, tags, sourceType);
        if (request.booleanArgument("confirm")) {
            return executeConfirmed(request.sessionKey(), pending);
        }
        pendingOperations.put(request.sessionKey(), pending);
        return WechatReply.text("请确认是否执行批量删除。确认后回复“确认”。");
    }

    private WechatReply confirmReindex(WechatToolRequest request) {
        long documentId = parseLong(argument(request, "document_id"));
        if (documentId <= 0) {
            return WechatReply.text("重新向量化需要提供 document_id。");
        }
        PendingOperation pending = PendingOperation.reindex(documentId);
        if (request.booleanArgument("confirm")) {
            return executeConfirmed(request.sessionKey(), pending);
        }
        pendingOperations.put(request.sessionKey(), pending);
        return WechatReply.text("请确认是否重新向量化知识文档：document_id=" + documentId + "。确认后回复“确认”。");
    }

    private WechatReply executeConfirmed(String sessionKey, PendingOperation pending) {
        return switch (pending.operation()) {
            case "delete" -> WechatReply.text(manageService.delete(sessionKey, pending.documentId())
                    ? "已删除知识文档：document_id=" + pending.documentId()
                    : "没有找到可删除的知识文档：document_id=" + pending.documentId());
            case "batch_delete" -> WechatReply.text("已批量删除知识文档：" + executeBatchDelete(sessionKey, pending) + " 条");
            case "reindex" -> WechatReply.text(manageService.reindex(sessionKey, pending.documentId())
                    ? "已重新向量化知识文档：document_id=" + pending.documentId()
                    : "当前版本无法安全重新向量化该知识文档；请重新添加原始资料。document_id=" + pending.documentId());
            default -> WechatReply.text("没有待确认的知识库操作。");
        };
    }

    private int executeBatchDelete(String sessionKey, PendingOperation pending) {
        if (!pending.documentIds().isBlank()) {
            int count = 0;
            for (String id : pending.documentIds().split("[,，]")) {
                long documentId = parseLong(id);
                if (documentId > 0 && manageService.delete(sessionKey, documentId)) {
                    count++;
                }
            }
            return count;
        }
        return manageService.batchDelete(sessionKey, pending.keyword(), pending.tags(), pending.sourceType());
    }

    private String formatList(List<KnowledgeDocument> documents) {
        if (documents.isEmpty()) {
            return "知识库目前没有保存资料。";
        }
        StringBuilder text = new StringBuilder("知识库资料列表：");
        for (KnowledgeDocument document : documents) {
            text.append("\n- document_id=").append(document.id())
                    .append("，标题：").append(document.title())
                    .append("，来源：").append(document.sourceType())
                    .append("，标签：").append(document.tags())
                    .append("，片段：").append(document.chunkCount());
        }
        return text.toString();
    }

    private String formatDocument(KnowledgeDocument document) {
        return """
                知识文档详情：
                document_id=%d
                标题：%s
                来源类型：%s
                来源链接：%s
                标签：%s
                切分片段：%d
                """.formatted(
                document.id(),
                document.title(),
                document.sourceType(),
                document.sourceUrl(),
                document.tags(),
                document.chunkCount()).strip();
    }

    private boolean isConfirmation(WechatToolRequest request) {
        String text = request.userText();
        return request.booleanArgument("confirm")
                || "确认".equals(text)
                || "确定".equals(text)
                || "是的".equals(text)
                || "执行".equals(text);
    }

    private long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String argument(WechatToolRequest request, String name) {
        return request == null ? "" : request.argument(name);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private record PendingOperation(
            String operation,
            long documentId,
            String documentIds,
            String keyword,
            String tags,
            String sourceType) {

        private static PendingOperation delete(long documentId) {
            return new PendingOperation("delete", documentId, "", "", "", "");
        }

        private static PendingOperation batchDelete(String documentIds, String keyword, String tags, String sourceType) {
            return new PendingOperation("batch_delete", 0, safe(documentIds), safe(keyword), safe(tags), safe(sourceType));
        }

        private static PendingOperation reindex(long documentId) {
            return new PendingOperation("reindex", documentId, "", "", "", "");
        }

        private static String safe(String value) {
            return value == null ? "" : value.strip();
        }
    }
}
