package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.web.context.WebResourceContextService;
import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.service.WebReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Function Calling 网页阅读工具。
 *
 * <p>这个工具负责读取用户给出的公开网页 URL，并把标题、来源和正文片段返回给模型。
 * 当用户明确表达“保存、记住、以后参考、加入知识库”等意图时，会把网页正文写入个人知识库。</p>
 */
@Component
public class WebReadWechatTool implements WechatTool {

    private static final int MAX_VISIBLE_CONTENT = 3500;

    private final WebReadService webReadService;
    private final KnowledgeIngestionService ingestionService;
    private final WebResourceContextService resourceContextService;

    public WebReadWechatTool(WebReadService webReadService, KnowledgeIngestionService ingestionService) {
        this(webReadService, ingestionService, new WebResourceContextService());
    }

    @Autowired
    public WebReadWechatTool(
            WebReadService webReadService,
            KnowledgeIngestionService ingestionService,
            WebResourceContextService resourceContextService) {
        this.webReadService = webReadService;
        this.ingestionService = ingestionService;
        this.resourceContextService = resourceContextService == null
                ? new WebResourceContextService()
                : resourceContextService;
    }

    @Override
    public String name() {
        return "web_read";
    }

    @Override
    public String description() {
        return "读取用户提供的公开网页 URL，提取标题和正文；用户要求保存、记住或以后参考时，自动加入知识库。";
    }

    @Override
    public List<String> arguments() {
        return List.of("url", "save_to_knowledge");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("url", "要读取的公开网页 URL", "https://example.com/article"),
                WechatToolParameter.optionalBoolean("save_to_knowledge", "是否将网页正文保存到知识库", false));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "读取公开网页正文并返回给大模型总结，必要时保存到知识库。",
                List.of(
                        "只支持 http/https；不支持登录、验证码、本机和内网地址。",
                        "网页摘要不等同于长期知识；只有用户要求保存、记住或以后参考时才入库。"),
                List.of("需要 url。"),
                List.of("返回网页标题、来源 URL、正文片段；保存时返回知识库 document_id。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String url = argument(request, "url");
        if (url.isBlank()) {
            url = resourceContextService.resolveUrl(request.sessionKey(), request.userText()).orElse("");
        }
        if (url.isBlank()) {
            return WechatReply.text("请提供要阅读的网页链接。");
        }

        WebPageContent page = webReadService.read(url);
        resourceContextService.recordRead(request.sessionKey(), page);
        StringBuilder text = new StringBuilder();
        text.append("网页已读取：").append(page.title())
                .append("\n来源：").append(page.url());

        if (shouldSaveToKnowledge(request)) {
            KnowledgeIngestionResult result = ingestionService.add(
                    request.sessionKey(),
                    page.title(),
                    page.content(),
                    "web",
                    page.url(),
                    "web");
            text.append("\n已保存到知识库：document_id=").append(result.documentId())
                    .append("，切分片段：").append(result.chunkCount());
        }

        text.append("\n\n正文片段：\n").append(truncate(page.content(), MAX_VISIBLE_CONTENT));
        return WechatReply.text(text.toString());
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

    private String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String argument(WechatToolRequest request, String name) {
        return request == null ? "" : request.argument(name);
    }
}
