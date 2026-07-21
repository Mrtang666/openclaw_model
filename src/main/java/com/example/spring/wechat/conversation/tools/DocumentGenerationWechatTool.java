package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.document.model.DocumentFormat;
import com.example.spring.wechat.document.model.GeneratedDocument;
import com.example.spring.wechat.document.model.GeneratedDocumentRequest;
import com.example.spring.wechat.document.service.DefaultDocumentGenerationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 微信端文档生成工具。
 *
 * <p>这个工具负责把用户需求或最近文件上下文整理成可发送的文档文件。
 * 如果工具参数已经提供完整正文，就直接写入；如果参数只是“上一步总结的结果”这类占位句，
 * 就重新调用文本大模型，根据最近文件上下文生成真正的正文。</p>
 */
@Component
public class DocumentGenerationWechatTool implements WechatTool {

    private final ChatService chatService;
    private final DefaultDocumentGenerationService documentGenerationService;

    public DocumentGenerationWechatTool(
            ChatService chatService,
            DefaultDocumentGenerationService documentGenerationService) {
        this.chatService = chatService;
        this.documentGenerationService = documentGenerationService;
    }

    @Override
    public String name() {
        return "document_generation";
    }

    @Override
    public String description() {
        return "根据用户需求或最近文件上下文生成 DOCX、PDF、TXT、Markdown 文档。缺少关键字段时应先追问用户。";
    }

    @Override
    public List<String> arguments() {
        return List.of("format", "title", "template", "requirement", "content", "previous_result");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        DocumentFormat format = DocumentFormat.fromName(firstNonBlank(
                request.argument("format"),
                request.argument("file_type")));
        String title = firstNonBlank(request.argument("title"), inferTitle(request.userText()));
        String template = firstNonBlank(request.argument("template"), "default");
        String explicitContent = firstNonBlank(request.argument("content"), request.argument("previous_result"));
        String content = shouldUseExplicitContent(explicitContent)
                ? explicitContent
                : generateContent(request);

        GeneratedDocument generated = documentGenerationService.generate(
                new GeneratedDocumentRequest(title, content, template),
                format);
        return WechatReply.ordered(List.of(WechatReply.Part.file(new WechatReply.FileAttachment(
                generated.bytes(),
                generated.fileName(),
                generated.contentType(),
                generated.caption()))));
    }

    private String generateContent(WechatToolRequest request) {
        return chatService.reply(buildPrompt(request));
    }

    private String buildPrompt(WechatToolRequest request) {
        return """
                你是文档写作助手，请根据用户需求生成可以直接写入文档的正文。
                要求：
                1. 只输出文档正文，不要解释你做了什么。
                2. 如果有最近文件上下文，要优先根据文件内容整理，不要只复述“可用上下文”。
                3. 如果工具参数里的 content 是“上一步总结的结果”这类占位句，要忽略它，重新生成真正正文。
                4. 内容要结构清楚，适合直接写入 Word、PDF、TXT 或 Markdown。
                5. 如果用户要求总结报告、汇报、方案、会议纪要、学习笔记，要使用对应的正式结构。

                最近上下文：
                %s

                用户当前需求：
                %s

                工具参数：
                %s
                """.formatted(request.historyText(), request.userText(), request.arguments());
    }

    private boolean shouldUseExplicitContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String normalized = content.strip();
        if (normalized.length() < 30) {
            return false;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        return !(normalized.contains("上一步总结")
                || normalized.contains("上一步的结果")
                || normalized.contains("上一工具")
                || normalized.contains("前面总结")
                || normalized.contains("刚才总结")
                || normalized.contains("总结后的内容")
                || normalized.contains("总结结果")
                || lower.contains("previous result")
                || lower.contains("previous_result")
                || lower.contains("last result"));
    }

    private String inferTitle(String userText) {
        if (userText == null || userText.isBlank()) {
            return "生成文档";
        }
        String text = userText.strip();
        return text.length() <= 18 ? text : text.substring(0, 18);
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
}
