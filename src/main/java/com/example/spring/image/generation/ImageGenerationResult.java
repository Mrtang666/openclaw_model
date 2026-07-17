package com.example.spring.image.generation;

public record ImageGenerationResult(
        String prompt,
        String imageUrl,
        byte[] imageBytes,
        String fileName,
        String contentType,
        Integer width,
        Integer height) {
}
