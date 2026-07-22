package com.example.file;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.CDNMedia;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

public class FileMediaDownloadService {

    private static final Logger log = LoggerFactory.getLogger(FileMediaDownloadService.class);
    private static final String CDN_DOWNLOAD_BASE = "https://novac2c.cdn.weixin.qq.com/c2c/download?encrypted_query_param=";
    private static final int MAGIC_BYTES = 12;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public byte[] download(ILinkClient client, MessageItem item) throws IOException {
        FileItem file = item == null ? null : item.getFile_item();
        logMediaMetadata(file);

        try {
            byte[] bytes = client.downloadFileFromMessageItem(item);
            log.info("SDK 文件下载成功: file={}, bytes={}, magic={}",
                    fileName(file), bytes == null ? 0 : bytes.length, magic(bytes));
            return bytes;
        } catch (Exception sdkError) {
            log.warn("SDK 文件下载失败，准备尝试 CDN 降级: file={}, error={}, root={}",
                    fileName(file), sdkError.getMessage(), rootMessage(sdkError));
            log.debug("SDK 文件下载异常堆栈", sdkError);
            return downloadWithFallback(file, sdkError);
        }
    }

    public String userMessage(Exception error) {
        String message = error == null ? "" : String.valueOf(error.getMessage());
        if (message.contains("decrypt media failed") || message.contains("解密失败")) {
            return "文件读取失败：微信文件下载成功但解密失败。请重新发送原始文件，尽量不要转发文件；如果还是失败，可以把文件另存后再重新上传。";
        }
        return "文件读取失败：" + message;
    }

    private byte[] downloadWithFallback(FileItem file, Exception sdkError) throws IOException {
        CDNMedia media = file == null ? null : file.getMedia();
        byte[] raw = downloadRaw(media);
        log.info("CDN 原始文件下载成功: file={}, bytes={}, magic={}",
                fileName(file), raw.length, magic(raw));

        if (looksLikePlainFile(raw, fileName(file))) {
            log.warn("CDN 返回内容看起来已是原始文件，跳过 AES 解密: file={}, magic={}",
                    fileName(file), magic(raw));
            return raw;
        }

        try {
            byte[] decrypted = decryptAesEcb(raw, media.getAes_key(), true);
            log.info("CDN 手动 AES 解密成功: file={}, bytes={}, magic={}",
                    fileName(file), decrypted.length, magic(decrypted));
            return decrypted;
        } catch (Exception paddedError) {
            log.debug("AES/PKCS5Padding 解密失败，尝试 NoPadding: {}", paddedError.getMessage());
            try {
                byte[] decrypted = decryptAesEcb(raw, media.getAes_key(), false);
                byte[] stripped = stripPkcsPaddingIfPresent(decrypted);
                log.info("CDN 手动 AES NoPadding 解密成功: file={}, bytes={}, magic={}",
                        fileName(file), stripped.length, magic(stripped));
                return stripped;
            } catch (Exception noPaddingError) {
                IOException wrapped = new IOException(
                        "微信文件下载成功但解密失败，可能是媒体密钥与 CDN 内容不匹配。请重新发送原始文件。", noPaddingError);
                wrapped.addSuppressed(sdkError);
                throw wrapped;
            }
        }
    }

    private byte[] downloadRaw(CDNMedia media) throws IOException {
        if (media == null) {
            throw new IOException("文件媒体信息为空");
        }
        String query = media.getEncrypt_query_param();
        if (query == null || query.trim().isEmpty()) {
            throw new IOException("文件 CDN 参数为空");
        }

        String url = CDN_DOWNLOAD_BASE + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("CDN 下载失败，HTTP " + response.statusCode());
            }
            return response.body() == null ? new byte[0] : response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("CDN 下载被中断", e);
        }
    }

    private byte[] decryptAesEcb(byte[] encrypted, String aesKey, boolean padded) throws Exception {
        byte[] key = decodeAesKey(aesKey);
        Cipher cipher = Cipher.getInstance(padded ? "AES/ECB/PKCS5Padding" : "AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(encrypted);
    }

    private byte[] decodeAesKey(String aesKey) throws IOException {
        if (aesKey == null || aesKey.trim().isEmpty()) {
            throw new IOException("文件 AES 密钥为空");
        }
        String value = aesKey.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length == 16) {
                return decoded;
            }
            String text = new String(decoded, StandardCharsets.UTF_8).trim();
            if (isHexKey(text)) {
                return hexToBytes(text);
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (isHexKey(value)) {
            return hexToBytes(value);
        }
        if (value.length() == 16) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        throw new IOException("文件 AES 密钥格式不支持，长度=" + value.length());
    }

    private byte[] stripPkcsPaddingIfPresent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        int pad = bytes[bytes.length - 1] & 0xff;
        if (pad <= 0 || pad > 16 || pad > bytes.length) {
            return bytes;
        }
        for (int i = bytes.length - pad; i < bytes.length; i++) {
            if ((bytes[i] & 0xff) != pad) {
                return bytes;
            }
        }
        byte[] result = new byte[bytes.length - pad];
        System.arraycopy(bytes, 0, result, 0, result.length);
        return result;
    }

    private boolean looksLikePlainFile(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        if (startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) return true;
        if (bytes[0] == 'P' && bytes[1] == 'K') return true;
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return true;
        if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) return true;

        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") || lower.endsWith(".json")) {
            return mostlyText(bytes);
        }
        return false;
    }

    private boolean mostlyText(byte[] bytes) {
        int checked = Math.min(bytes.length, 1024);
        int printable = 0;
        for (int i = 0; i < checked; i++) {
            int b = bytes[i] & 0xff;
            if (b == 9 || b == 10 || b == 13 || (b >= 32 && b < 127) || b >= 128) {
                printable++;
            }
        }
        return checked > 0 && printable * 100 / checked > 85;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isHexKey(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{32}");
    }

    private byte[] hexToBytes(String value) throws IOException {
        if (value.length() % 2 != 0) {
            throw new IOException("HEX 密钥长度不正确");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private void logMediaMetadata(FileItem file) {
        CDNMedia media = file == null ? null : file.getMedia();
        log.info("收到文件媒体信息: file={}, len={}, md5={}, hasMedia={}, encryptType={}, hasAesKey={}, aesKeyLength={}, hasQuery={}, queryLength={}",
                fileName(file),
                file == null ? null : file.getLen(),
                file == null ? null : file.getMd5(),
                media != null,
                media == null ? null : media.getEncrypt_type(),
                media != null && media.getAes_key() != null && !media.getAes_key().isBlank(),
                media == null || media.getAes_key() == null ? 0 : media.getAes_key().length(),
                media != null && media.getEncrypt_query_param() != null && !media.getEncrypt_query_param().isBlank(),
                media == null || media.getEncrypt_query_param() == null ? 0 : media.getEncrypt_query_param().length());
    }

    private String fileName(FileItem file) {
        return file == null ? "unknown" : file.getFile_name();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "" : current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private String magic(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(bytes.length, MAGIC_BYTES);
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format("%02X", bytes[i] & 0xff));
        }
        return builder.toString();
    }
}
