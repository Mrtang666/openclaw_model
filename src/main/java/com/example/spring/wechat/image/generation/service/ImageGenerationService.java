package com.example.spring.wechat.image.generation.service;

import com.example.spring.wechat.image.generation.ImageGenerationClient;
import com.example.spring.wechat.image.generation.ImageGenerationException;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 图片生成应用服务。
 * 负责补齐默认尺寸、水印等参数，调用图片生成客户端，并在拿到图片 URL 后下载图片字节，
 * 这样微信发送层可以直接发送图片文件，而不是只返回一个链接。
 */
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
            throw new ImageGenerationException("图片生成接口未返回图片地址");
        }

        try {
            ResponseEntity<byte[]> response = downloadClient.get()
                    .uri(URI.create(generated.imageUrl()))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] imageBytes = response.getBody();
            if (imageBytes == null || imageBytes.length == 0) {
                throw new ImageGenerationException("图片下载结果为空");
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
            throw new ImageGenerationException("下载生成图片失败", exception);
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
            // URL 解析失败时使用默认图片类型，避免因为文件名异常影响主流程。
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
