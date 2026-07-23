package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Function Calling 知识库查询工具。
 */
@Component
public class KnowledgeQueryWechatTool implements WechatTool {

    private final KnowledgeSearchService searchService;

    public KnowledgeQueryWechatTool(KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return "knowledge_query";
    }

    @Override
    public String description() {
        return "当用户要求根据知识库、保存过的资料或项目资料回答问题时，从个人知识库检索相关片段";
    }

    @Override
    public List<String> arguments() {
        return List.of("question", "top_k", "tags");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("question", "要查询知识库的问题", "我的项目 Function Calling 流程是什么？"),
                WechatToolParameter.optionalString("top_k", "返回片段数量，默认 5", "5"),
                WechatToolParameter.optionalString("tags", "限定标签，多个标签用逗号分隔", "agent"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "从用户个人知识库检索相关资料片段，供大模型基于资料回答。",
                List.of("只代表知识库中检索到的资料；没有结果时必须说明未找到，不能编造。"),
                List.of("需要 question；如果用户问题为空，应追问。"),
                List.of("返回相关知识片段、来源标题、来源 URL 和匹配分数。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String question = argument(request, "question");
        if (question.isBlank()) {
            question = request == null ? "" : request.userText();
        }
        List<KnowledgeSearchResult> results = searchService.search(
                request.sessionKey(),
                question,
                parseInt(argument(request, "top_k"), 5),
                argument(request, "tags"));
        if (results.isEmpty()) {
            return WechatReply.text("知识库中没有找到和这个问题相关的资料。");
        }
        StringBuilder text = new StringBuilder("相关知识片段：");
        for (int index = 0; index < results.size(); index++) {
            KnowledgeSearchResult result = results.get(index);
            text.append("\n\n[").append(index + 1).append("] 标题：").append(result.title())
                    .append("\ndocument_id=").append(result.documentId())
                    .append("\n匹配分：").append(String.format(java.util.Locale.ROOT, "%.3f", result.score()));
            if (result.sourceUrl() != null && !result.sourceUrl().isBlank()) {
                text.append("\n来源：").append(result.sourceUrl());
            }
            text.append("\n内容：").append(result.content());
        }
        return WechatReply.text(text.toString());
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
}
