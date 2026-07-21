package com.example.spring.wechat.document.model;

import java.util.List;

/**
 * 文档解析后的结果，既包含原始文本，也包含摘要和分块内容。
 */
public record ParsedDocument(
        String fileName,
        String mimeType,
        DocumentFormat format,
        String fullText,
        String summary,
        List<DocumentChunk> chunks) {

    public ParsedDocument {
        fileName = fileName == null ? "" : fileName.strip();
        mimeType = mimeType == null ? "" : mimeType.strip();
        format = format == null ? DocumentFormat.UNKNOWN : format;
        fullText = fullText == null ? "" : fullText.strip();
        summary = summary == null ? "" : summary.strip();
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
