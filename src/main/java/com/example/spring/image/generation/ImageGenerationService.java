package com.example.spring.image.generation;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class ImageGenerationService {

    private final ImageGenerationClient client;
    private final RestClient downloadClient;

    public ImageGenerationService(ImageGenerationClient client, RestClient.Builder downloadBuilder) {
        this.client = client;
        this.downloadClient = downloadBuilder.build();
    }

    public ImageGenerationResult generate(ImageGenerationRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new ImageGenerationException("请告诉我你想生成什么图片");
        }

        ImageGenerationResult generated = client.generate(request);
        if (generated == null || generated.imageUrl() == null || generated.imageUrl().isBlank()) {
            throw new ImageGenerationException("图片生成失败，请稍后重试");
        }

        try {
            ResponseEntity<byte[]> response = downloadClient.get()
                    .uri(URI.create(generated.imageUrl()))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] imageBytes = response.getBody();
            if (imageBytes == null || imageBytes.length == 0) {
                throw new ImageGenerationException("图片下载失败，请稍后重试");
            }

            String contentType = response.getHeaders().getContentType() == null
                    ? defaultContentType(generated.imageUrl())
                    : response.getHeaders().getContentType().toString();

            return new ImageGenerationResult(
                    generated.prompt(),
                    generated.imageUrl(),
                    imageBytes,
                    fileName(generated),
                    contentType,
                    generated.width(),
                    generated.height());
        } catch (ImageGenerationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ImageGenerationException("图片下载失败，请稍后重试", exception);
        }
    }

    private String fileName(ImageGenerationResult generated) {
        if (generated.fileName() != null && !generated.fileName().isBlank()) {
            return generated.fileName();
        }

        try {
            Path path = Path.of(new URI(generated.imageUrl()).getPath());
            Path fileName = path.getFileName();
            if (fileName != null && !fileName.toString().isBlank()) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
            // Fall through to the default name.
        }

        return "generated-image.png";
    }

    private String defaultContentType(String imageUrl) {
        String lowerCaseUrl = imageUrl == null ? "" : imageUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith(".jpg") || lowerCaseUrl.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseUrl.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
