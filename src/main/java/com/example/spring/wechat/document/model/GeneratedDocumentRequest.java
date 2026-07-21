package com.example.spring.wechat.document.model;

/**
 * 文档生成请求。
 */
public record GeneratedDocumentRequest(
        String title,
        String content,
        String templateName) {

    public GeneratedDocumentRequest {
        title = title == null || title.isBlank() ? "未命名文档" : title.strip();
        content = content == null ? "" : content.strip();
        templateName = templateName == null || templateName.isBlank() ? "default" : templateName.strip();
    }
}
