package com.example.spring.agent;

import java.util.Objects;

public record ImageAsset(byte[] data, String mediaType, String fileName) {
    public ImageAsset {
        Objects.requireNonNull(data, "data");
        data = data.clone();
        mediaType = mediaType == null || mediaType.isBlank() ? "image/jpeg" : mediaType;
        fileName = fileName == null || fileName.isBlank() ? "image.jpg" : fileName;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
