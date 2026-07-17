package com.example.spring.wechat.image.service;

import com.example.spring.wechat.client.ImageSourceType;
import com.example.spring.wechat.client.WechatIncomingImage;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.wechat.image.ImageUnderstandingException;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ImageInputResolver {

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://[^\\s\"'<>]+\\.(?:png|jpg|jpeg|gif|bmp|webp|tiff)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URI_PATTERN = Pattern.compile(
            "data:image/[^\\s\"'<>]+;base64,[A-Za-z0-9+/=]+",
            Pattern.CASE_INSENSITIVE);

    public ImageAnalysisRequest resolve(WechatIncomingMessage message) {
        if (message == null) {
            throw new ImageUnderstandingException("图片消息不能为空");
        }

        String text = normalizeText(message.text());
        List<WechatIncomingImage> images = new ArrayList<>();
        if (message.images() != null) {
            for (WechatIncomingImage image : message.images()) {
                if (image != null) {
                    images.add(enrichAttachment(image));
                }
            }
        }

        text = extractTextImages(text, images);
        return new ImageAnalysisRequest(text, images);
    }

    private String extractTextImages(String text, List<WechatIncomingImage> images) {
        String result = text;
        for (String dataUri : findMatches(DATA_URI_PATTERN, result)) {
            images.add(enrichDataUri(dataUri));
            result = result.replace(dataUri, " ");
        }

        for (String imageUrl : findMatches(IMAGE_URL_PATTERN, result)) {
            images.add(new WechatIncomingImage(ImageSourceType.TEXT_URL, imageUrl));
            result = result.replace(imageUrl, " ");
        }

        return normalizeText(result);
    }

    private List<String> findMatches(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matches;
        }

        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    private WechatIncomingImage enrichAttachment(WechatIncomingImage image) {
        if (!image.hasBytes()) {
            return image;
        }

        String mimeType = image.mimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = guessMimeType(image.bytes(), image.fileName());
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(image.bytes())) {
            BufferedImage bufferedImage = ImageIO.read(input);
            if (bufferedImage == null) {
                throw new ImageUnderstandingException("无法识别图片格式");
            }

            String colorMode = bufferedImage.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY
                    ? "GRAYSCALE"
                    : "COLOR";
            String fileName = image.fileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = "wechat-image." + fileExtensionForMimeType(mimeType);
            }
            return image.withMetadata(
                    mimeType,
                    fileName,
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    colorMode);
        } catch (IOException exception) {
            throw new ImageUnderstandingException("解析图片元数据失败", exception);
        }
    }

    private WechatIncomingImage enrichDataUri(String dataUri) {
        ParsedDataUri parsed = parseDataUri(dataUri);
        if (parsed == null) {
            throw new ImageUnderstandingException("无法解析图片 data URI");
        }
        return enrichAttachment(new WechatIncomingImage(
                ImageSourceType.INLINE_DATA_URI,
                dataUri,
                parsed.bytes(),
                parsed.mimeType(),
                "inline-image." + fileExtensionForMimeType(parsed.mimeType()),
                null,
                null,
                null));
    }

    private ParsedDataUri parseDataUri(String dataUri) {
        if (dataUri == null || !dataUri.startsWith("data:image/")) {
            return null;
        }

        int commaIndex = dataUri.indexOf(',');
        if (commaIndex < 0) {
            return null;
        }

        String meta = dataUri.substring(5, commaIndex);
        String payload = dataUri.substring(commaIndex + 1);
        int semicolonIndex = meta.indexOf(';');
        String mimeType = semicolonIndex < 0 ? meta : meta.substring(0, semicolonIndex);
        if (mimeType.isBlank()) {
            mimeType = "image/png";
        }
        byte[] bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.US_ASCII));
        return new ParsedDataUri(mimeType, bytes);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.strip()
                .replaceAll("[\\s\\u00A0]+", " ")
                .replaceAll("\\s+([，。！？、；：,!.?])", "$1")
                .strip();
    }

    private String guessMimeType(byte[] bytes, String fileName) {
        if (bytes != null) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                String mimeType = URLConnection.guessContentTypeFromStream(input);
                if (mimeType != null && !mimeType.isBlank()) {
                    return mimeType;
                }
            } catch (IOException ignored) {
                // ByteArrayInputStream close is a no-op, keep fallback.
            }
        }

        if (fileName != null) {
            String extension = fileExtension(fileName);
            return switch (extension.toLowerCase()) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                case "webp" -> "image/webp";
                case "tif", "tiff" -> "image/tiff";
                default -> "image/png";
            };
        }

        return "image/png";
    }

    private String fileExtension(String value) {
        if (value == null || value.isBlank()) {
            return "png";
        }
        String trimmed = value.strip();
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == trimmed.length() - 1) {
            return "png";
        }
        return trimmed.substring(dotIndex + 1);
    }

    private String fileExtensionForMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "png";
        }

        String normalized = mimeType.strip().toLowerCase();
        if (normalized.contains("png")) {
            return "png";
        }
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return "jpg";
        }
        if (normalized.contains("gif")) {
            return "gif";
        }
        if (normalized.contains("bmp")) {
            return "bmp";
        }
        if (normalized.contains("webp")) {
            return "webp";
        }
        if (normalized.contains("tiff") || normalized.contains("tif")) {
            return "tiff";
        }
        return "png";
    }

    private record ParsedDataUri(String mimeType, byte[] bytes) {
    }
}
