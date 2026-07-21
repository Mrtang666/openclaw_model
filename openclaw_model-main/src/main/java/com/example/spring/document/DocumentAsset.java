package com.example.spring.document;

public record DocumentAsset(
    String documentId,
    String fileName,
    String mediaType,
    byte[] data,
    String extractedText,
    String description) {

    public DocumentAsset {
        documentId = documentId == null ? "" : documentId;
        fileName = fileName == null || fileName.isBlank() ? "document" : fileName;
        mediaType = mediaType == null ? "application/octet-stream" : mediaType;
        data = data == null ? new byte[0] : data.clone();
        extractedText = extractedText == null ? "" : extractedText.trim();
        description = description == null ? "" : description.trim();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
