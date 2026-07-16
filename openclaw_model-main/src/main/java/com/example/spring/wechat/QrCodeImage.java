package com.example.spring.wechat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

final class QrCodeImage {
    private static final String BASE64_MARKER = ";base64,";
    private static final int QR_CODE_SIZE = 480;

    private QrCodeImage() {
    }

    static Path save(String imageContent, Path outputFile) throws IOException {
        if (imageContent == null || imageContent.isBlank()) {
            throw new IOException("微信接口未返回二维码内容");
        }

        String content = imageContent.trim();
        if (isHttpUrl(content)) {
            return writeQrCode(content, outputFile);
        }

        int markerIndex = content.indexOf(BASE64_MARKER);
        if (markerIndex >= 0) {
            content = content.substring(markerIndex + BASE64_MARKER.length());
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getMimeDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new IOException("无法识别二维码内容格式", exception);
        }

        Path absoluteOutputFile = prepareOutputFile(outputFile);
        Files.write(absoluteOutputFile, imageBytes);
        return absoluteOutputFile;
    }

    private static Path writeQrCode(String content, Path outputFile) throws IOException {
        Path absoluteOutputFile = prepareOutputFile(outputFile);
        try {
            BitMatrix matrix =
                new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    QR_CODE_SIZE,
                    QR_CODE_SIZE);
            MatrixToImageWriter.writeToPath(matrix, "PNG", absoluteOutputFile);
            return absoluteOutputFile;
        } catch (WriterException exception) {
            throw new IOException("二维码链接渲染失败", exception);
        }
    }

    private static Path prepareOutputFile(Path outputFile) throws IOException {
        Path absoluteOutputFile = outputFile.toAbsolutePath();
        Path parent = absoluteOutputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return absoluteOutputFile;
    }

    static boolean open(Path imageFile) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop.getDesktop().open(imageFile.toFile());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean openUrl(String content) {
        if (content == null || !Desktop.isDesktopSupported()) {
            return false;
        }
        String value = content.trim();
        if (!isHttpUrl(value)) {
            return false;
        }
        try {
            Desktop.getDesktop().browse(URI.create(value));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("https://") || value.startsWith("http://");
    }
}
