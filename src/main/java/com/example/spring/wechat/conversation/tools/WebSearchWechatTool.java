package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.web.context.WebResourceContextService;
import com.example.spring.wechat.web.model.WebSearchResult;
import com.example.spring.wechat.web.service.WebSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Function Calling 网页搜索工具。
 *
 * <p>搜索工具只返回标题、链接和摘要，不读取网页全文。用户要求“记住、保存、以后参考”时，
 * 入库的也是搜索摘要，避免把不完整或未经阅读的网页全文错误塞进长期上下文。</p>
 */
@Component
public class WebSearchWechatTool implements WechatTool {

    private final WebSearchService webSearchService;
    private final KnowledgeIngestionService ingestionService;
    private final WebResourceContextService resourceContextService;

    public WebSearchWechatTool(WebSearchService webSearchService, KnowledgeIngestionService ingestionService) {
        this(webSearchService, ingestionService, new WebResourceContextService());
    }

    @Autowired
    public WebSearchWechatTool(
            WebSearchService webSearchService,
            KnowledgeIngestionService ingestionService,
            WebResourceContextService resourceContextService) {
        this.webSearchService = webSearchService;
        this.ingestionService = ingestionService;
        this.resourceContextService = resourceContextService == null
                ? new WebResourceContextService()
                : resourceContextService;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "搜索互联网、查询最新资料或查找公开网页资料；如果用户直接提供 URL，优先使用 web_read。用户要求记住搜索结果时，只保存搜索摘要。";
    }

    @Override
    public List<String> arguments() {
        return List.of("query", "limit", "freshness", "language", "save_to_knowledge");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("query", "搜索关键词", "Qdrant Java 接入教程"),
                WechatToolParameter.optionalString("limit", "返回结果数量，默认 5", "5"),
                WechatToolParameter.optionalEnum("freshness", "时效范围", List.of("any", "day", "week", "month"), "any"),
                WechatToolParameter.optionalString("language", "搜索语言", "zh-CN"),
                WechatToolParameter.optionalBoolean("save_to_knowledge", "是否把搜索摘要保存到知识库", false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "搜索公开互联网资料并返回标题、链接和摘要。",
                List.of(
                        "搜索摘要不等于网页全文；需要准确总结某篇文章时，应继续调用 web_read。",
                        "普通搜索结果只用于当前任务；只有用户要求保存、记住或以后参考时才把搜索摘要入库。",
                        "未配置搜索服务时不能假装搜索过。"),
                List.of("需要 query。"),
                List.of("返回搜索结果标题、URL、摘要、来源和时间；保存时返回知识库 document_id。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String query = argument(request, "query");
        if (query.isBlank()) {
            return WechatReply.text("请告诉我你想搜索什么内容。");
        }

        List<WebSearchResult> results = webSearchService.search(
                query,
                parseInt(argument(request, "limit"), 5),
                defaultText(argument(request, "freshness"), "any"),
                defaultText(argument(request, "language"), "zh-CN"));
        if (results.isEmpty()) {
            return WechatReply.text("没有搜索到可用结果。");
        }

        resourceContextService.recordSearch(request.sessionKey(), query, results);
        String summary = searchSummary(query, results);
        StringBuilder text = new StringBuilder("网页搜索结果：")
                .append(summary.substring(summary.indexOf("\n\n") + 2))
                .append("\n\n参考来源：")
                .append(referenceList(results));
        if (shouldSaveToKnowledge(request)) {
            KnowledgeIngestionResult result = ingestionService.add(
                    request.sessionKey(),
                    "网页搜索结果：" + query,
                    summary,
                    "web_search",
                    "",
                    "web,search");
            text.append("\n\n已保存到知识库：document_id=").append(result.documentId())
                    .append("，切分片段：").append(result.chunkCount());
        }
        return WechatReply.text(text.toString());
    }

    private String searchSummary(String query, List<WebSearchResult> results) {
        StringBuilder text = new StringBuilder("搜索词：").append(query);
        for (int index = 0; index < results.size(); index++) {
            WebSearchResult result = results.get(index);
            text.append("\n\n[来源").append(index + 1).append("] ").append(result.title());
            if (result.url() != null && !result.url().isBlank()) {
                text.append("\n链接：").append(result.url());
            }
            if (result.source() != null && !result.source().isBlank()) {
                text.append("\n来源：").append(result.source());
            }
            if (result.publishedAt() != null && !result.publishedAt().isBlank()) {
                text.append("\n时间：").append(result.publishedAt());
            }
            if (result.snippet() != null && !result.snippet().isBlank()) {
                text.append("\n摘要：").append(result.snippet());
            }
        }
        return text.toString();
    }

    private String referenceList(List<WebSearchResult> results) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            WebSearchResult result = results.get(index);
            if (result == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("[来源").append(index + 1).append("] ")
                    .append(defaultText(result.title(), "未命名网页"));
            if (result.url() != null && !result.url().isBlank()) {
                text.append("：").append(result.url());
            }
        }
        return text.toString();
    }

    private boolean shouldSaveToKnowledge(WechatToolRequest request) {
        return request != null
                && (request.booleanArgument("save_to_knowledge")
                || containsKnowledgeSaveIntent(request.userText()));
    }

    private boolean containsKnowledgeSaveIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.toLowerCase(java.util.Locale.ROOT);
        return value.contains("记住")
                || value.contains("保存")
                || value.contains("存下来")
                || value.contains("加入知识库")
                || value.contains("放到知识库")
                || value.contains("以后参考")
                || value.contains("后续参考")
                || value.contains("之后参考")
                || value.contains("以后回答")
                || value.contains("以后用")
                || value.contains("keep this")
                || value.contains("save this")
                || value.contains("remember this");
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
}
