package com.example.spring.wechat.document.model;

/**
 * 文档工具当前支持识别、解析或生成的文件格式。
 */
public enum DocumentFormat {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT("txt", "text/plain"),
    MD("md", "text/markdown"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    UNKNOWN("bin", "application/octet-stream");

    private final String extension;
    private final String contentType;

    DocumentFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static DocumentFormat fromExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        for (DocumentFormat format : values()) {
            if (format != UNKNOWN && lower.endsWith("." + format.extension)) {
                return format;
            }
        }
        return UNKNOWN;
    }

    public static DocumentFormat fromName(String value) {
        if (value == null || value.isBlank()) {
            return DOCX;
        }
        String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "pdf" -> PDF;
            case "docx", "word" -> DOCX;
            case "txt", "text" -> TXT;
            case "md", "markdown" -> MD;
            case "xlsx", "excel" -> XLSX;
            case "pptx", "ppt" -> PPTX;
            default -> fromExtension(normalized);
        };
    }
}
