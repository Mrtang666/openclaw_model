package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.document.model.ParsedDocument;
import com.example.spring.wechat.document.service.DocumentParseService;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信端文档解析工具。
 *
 * <p>负责读取用户发送的文件，输出文件类型、摘要和关键片段。
 * 如果本轮没有新文件，但上下文里有最近文件摘要，则基于最近文件摘要给出自然回复。</p>
 */
@Component
public class DocumentAnalysisWechatTool implements WechatTool {

    private final DocumentParseService documentParseService;

    public DocumentAnalysisWechatTool(DocumentParseService documentParseService) {
        this.documentParseService = documentParseService;
    }

    @Override
    public String name() {
        return "document_analysis";
    }

    @Override
    public String description() {
        return "解析用户发送的 PDF、Word、TXT、Markdown、Excel、PPT 文件，提取摘要、重点、表格或结构化内容。";
    }

    @Override
    public List<String> arguments() {
        return List.of("question", "operation");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        if (request.files().isEmpty()) {
            if (hasRecentFileContext(request.historyText())) {
                return WechatReply.text(buildFromHistory(request));
            }
            return WechatReply.text("我还没有收到可解析的文件，请先发送 PDF、Word、TXT、Markdown、Excel 或 PPT 文件。");
        }

        StringBuilder reply = new StringBuilder();
        for (WechatIncomingFile file : request.files()) {
            ParsedDocument parsed = documentParseService.parse(file);
            appendParsedDocument(reply, parsed);
        }
        return WechatReply.text(reply.toString().strip());
    }

    private void appendParsedDocument(StringBuilder reply, ParsedDocument parsed) {
        if (reply.length() > 0) {
            reply.append(System.lineSeparator()).append(System.lineSeparator());
        }
        reply.append("已解析文件《").append(parsed.fileName()).append("》。").append(System.lineSeparator())
                .append("文件类型：").append(parsed.format()).append(System.lineSeparator())
                .append("内容摘要：").append(parsed.summary()).append(System.lineSeparator());
        if (!parsed.chunks().isEmpty()) {
            reply.append("关键片段：").append(System.lineSeparator());
            parsed.chunks().stream().limit(5).forEach(chunk -> reply
                    .append("- ")
                    .append(chunk.summary())
                    .append(System.lineSeparator()));
        }
    }

    private boolean hasRecentFileContext(String historyText) {
        return historyText != null && historyText.contains("最近文件");
    }

    private String buildFromHistory(WechatToolRequest request) {
        String question = firstNonBlank(request.argument("question"), request.userText(), "总结最近文件");
        String fileContext = fileContextOnly(request.historyText());
        return """
                我会根据最近上传的文件摘要处理你的需求。

                你的需求：%s

                根据当前已经提取到的文件信息，可以先整理出以下内容：
                %s
                """.formatted(question, fileContext).strip();
    }

    private String fileContextOnly(String historyText) {
        if (historyText == null || historyText.isBlank()) {
            return "";
        }
        int conversationStart = historyText.indexOf("用户：");
        if (conversationStart >= 0) {
            return historyText.substring(0, conversationStart).strip();
        }
        return historyText.strip();
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
