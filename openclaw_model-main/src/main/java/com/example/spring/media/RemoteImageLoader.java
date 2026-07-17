package com.example.spring.media;

import com.example.spring.agent.ImageAsset;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RemoteImageLoader {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s<>]+" );
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private final HttpClient httpClient;
    private final boolean allowLoopback;

    public RemoteImageLoader() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(), false);
    }

    RemoteImageLoader(HttpClient httpClient, boolean allowLoopback) {
        this.httpClient = httpClient;
        this.allowLoopback = allowLoopback;
    }

    public List<ImageAsset> loadImagesFromText(String text)
        throws IOException, InterruptedException {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ImageAsset> images = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            try {
                images.add(load(candidate));
            } catch (IllegalArgumentException exception) {
                // Ordinary web links are not image inputs and should remain available to chat.
            }
        }
        return images;
    }

    public ImageAsset load(String url) throws IOException, InterruptedException {
        URI uri = validateUri(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "image/*")
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("图片地址返回 HTTP " + response.statusCode());
        }
        String mediaType = response.headers().firstValue("Content-Type")
            .orElse("").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mediaType.startsWith("image/")) {
            response.body().close();
            throw new IllegalArgumentException("URL 不是图片内容");
        }
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declaredLength > MAX_IMAGE_BYTES) {
            response.body().close();
            throw new IOException("图片超过 10MB 限制");
        }
        byte[] data;
        try (InputStream input = response.body()) {
            data = input.readNBytes(MAX_IMAGE_BYTES + 1);
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new IOException("图片超过 10MB 限制");
        }
        return new ImageAsset(data, mediaType, fileName(uri, mediaType));
    }

    private URI validateUri(String value) throws IOException {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("图片 URL 格式无效", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme())
            || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("图片 URL 仅支持 HTTP 或 HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("图片 URL 缺少主机名");
        }
        if (!allowLoopback) {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("不允许访问本机或内网图片地址");
                }
            }
        }
        return uri;
    }

    private static String fileName(URI uri, String mediaType) {
        String path = uri.getPath();
        if (path != null && path.contains("/")) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.isBlank() && name.length() <= 100) {
                return name;
            }
        }
        String extension = switch (mediaType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        return "remote-image" + extension;
    }

    private static String trimTrailingPunctuation(String url) {
        return url.replaceFirst("[，。！？、；：,.!?)\\]}]+$", "");
    }
}
