package com.example.spring.document;

public record GeneratedDocument(
    byte[] data,
    String mediaType,
    String fileName,
    String description) {

    public GeneratedDocument {
        data = data == null ? new byte[0] : data.clone();
        mediaType = mediaType == null ? "application/octet-stream" : mediaType;
        fileName = fileName == null || fileName.isBlank() ? "result.bin" : fileName;
        description = description == null ? "" : description.trim();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
