package com.example.spring.image.generation;

public record ImageGenerationRequest(
        String prompt,
        String styleHint,
        Integer width,
        Integer height,
        Boolean watermark) {

    public ImageGenerationRequest {
        prompt = normalize(prompt);
        styleHint = normalize(styleHint);
    }

    public ImageGenerationRequest(String prompt) {
        this(prompt, null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
