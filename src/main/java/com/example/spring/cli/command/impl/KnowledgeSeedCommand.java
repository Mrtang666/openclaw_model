package com.example.spring.cli.command.impl;

import com.example.spring.cli.command.core.Command;
import com.example.spring.wechat.knowledge.model.KnowledgeSeedDocument;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.knowledge.service.KnowledgeSeedDataset;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeSeedCommand implements Command {

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeSeedCommand(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public String name() {
        return "knowledge_seed";
    }

    @Override
    public String description() {
        return "导入内置测试知识样本到指定 session";
    }

    @Override
    public String execute(List<String> arguments) {
        String sessionKey = arguments.isEmpty() ? "demo-rag" : arguments.get(0).strip();
        if (sessionKey.isBlank()) {
            return usage();
        }

        List<KnowledgeSeedDocument> documents = KnowledgeSeedDataset.documents();
        StringBuilder output = new StringBuilder();
        output.append("开始导入测试知识样本，session_key=").append(sessionKey).append(System.lineSeparator());

        int imported = 0;
        int duplicates = 0;
        for (KnowledgeSeedDocument document : documents) {
            var result = ingestionService.add(
                    sessionKey,
                    document.title(),
                    document.content(),
                    document.sourceType(),
                    document.sourceUrl(),
                    document.tags());
            imported++;
            if (result.alreadyExists()) {
                duplicates++;
            }
            output.append(String.format(Locale.ROOT,
                    "- document_id=%d title=%s chunks=%d duplicate=%s%n",
                    result.documentId(),
                    result.title(),
                    result.chunkCount(),
                    result.alreadyExists()));
        }

        output.append("导入完成：").append(imported).append(" 条样本");
        if (duplicates > 0) {
            output.append("，重复 ").append(duplicates).append(" 条");
        }
        output.append(System.lineSeparator())
                .append("可直接使用 /knowledge_query 或 RAG 对话测试召回效果。");
        return output.toString().stripTrailing();
    }

    private String usage() {
        return "用法：/knowledge_seed <session_key>";
    }
}
