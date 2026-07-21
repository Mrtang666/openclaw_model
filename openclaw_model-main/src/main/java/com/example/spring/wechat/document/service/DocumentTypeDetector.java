package com.example.spring.wechat.document.service;

import com.example.spring.wechat.document.model.DocumentFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 负责综合文件头、MIME 类型和后缀名判断文档格式。
 */
@Component
public class DocumentTypeDetector {

    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};

    public DocumentFormat detect(String fileName, String mimeType, byte[] bytes) {
        if (startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) {
            return DocumentFormat.PDF;
        }

        DocumentFormat byMime = detectByMime(mimeType);
        if (byMime != DocumentFormat.UNKNOWN) {
            return byMime;
        }

        DocumentFormat byExtension = DocumentFormat.fromExtension(fileName);
        if (byExtension != DocumentFormat.UNKNOWN) {
            return byExtension;
        }

        if (startsWith(bytes, ZIP_MAGIC)) {
            return DocumentFormat.UNKNOWN;
        }

        if (looksLikeText(bytes)) {
            return DocumentFormat.TXT;
        }
        return DocumentFormat.UNKNOWN;
    }

    private DocumentFormat detectByMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return DocumentFormat.UNKNOWN;
        }
        String value = mimeType.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("pdf")) {
            return DocumentFormat.PDF;
        }
        if (value.contains("wordprocessingml") || value.contains("msword")) {
            return DocumentFormat.DOCX;
        }
        if (value.contains("spreadsheetml")) {
            return DocumentFormat.XLSX;
        }
        if (value.contains("presentationml")) {
            return DocumentFormat.PPTX;
        }
        if (value.contains("markdown")) {
            return DocumentFormat.MD;
        }
        if (value.startsWith("text/")) {
            return DocumentFormat.TXT;
        }
        return DocumentFormat.UNKNOWN;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int sample = Math.min(bytes.length, 512);
        int control = 0;
        for (int index = 0; index < sample; index++) {
            int value = bytes[index] & 0xff;
            if (value == 0) {
                return false;
            }
            if (value < 32 && value != '\n' && value != '\r' && value != '\t') {
                control++;
            }
        }
        return control < sample / 20;
    }
}
