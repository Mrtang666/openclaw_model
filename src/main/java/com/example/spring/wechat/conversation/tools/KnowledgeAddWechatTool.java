package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Function Calling 知识入库工具。
 */
@Component
public class KnowledgeAddWechatTool implements WechatTool {

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeAddWechatTool(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public String name() {
        return "knowledge_add";
    }

    @Override
    public String description() {
        return "个人知识库入库工具，保存用户明确要求长期复用的资料。";
    }

    @Override
    public List<String> arguments() {
        return List.of("title", "content", "source_type", "source_url", "tags");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("title", "知识标题", "Function Calling 项目说明"),
                WechatToolParameter.requiredString("content", "要保存到知识库的完整文本内容", "OpenClaw 使用 Function Calling 调用工具。"),
                WechatToolParameter.optionalEnum("source_type", "来源类型", List.of("text", "file", "web", "chat"), "text"),
                WechatToolParameter.optionalString("source_url", "网页或资料来源链接", "https://example.com/article"),
                WechatToolParameter.optionalString("tags", "标签，多个标签用逗号分隔", "agent,function-calling"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "保存用户明确要求长期复用的资料到个人知识库",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        KnowledgeIngestionResult result = ingestionService.add(
                request.sessionKey(),
                argument(request, "title"),
                argument(request, "content"),
                argument(request, "source_type"),
                argument(request, "source_url"),
                argument(request, "tags"));
        String prefix = result.alreadyExists() ? "这条知识已经存在，无需重复保存。" : "已保存到知识库。";
        return WechatReply.text("""
                %s
                document_id=%d
                标题：%s
                切分片段：%d
                """.formatted(prefix, result.documentId(), result.title(), result.chunkCount()).strip());
    }

    private String argument(WechatToolRequest request, String name) {
        return request == null ? "" : request.argument(name);
    }
}
