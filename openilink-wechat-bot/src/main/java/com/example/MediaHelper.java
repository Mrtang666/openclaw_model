package com.example;

import com.openilink.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class MediaHelper {

    private static final Logger log = LoggerFactory.getLogger(MediaHelper.class);
    private static final String DOWNLOADS_DIR = "downloads";
    private static final String CDN_BASE = "https://novac2c.cdn.weixin.qq.com/c2c";

    public static class MediaInfo {
        public MessageItemType type;
        public String aesKey;
        public String fileId;
        public String fileMd5;
        public Long fileSize;
        public String extension;
        public String fileName;
        public String voiceText;
        public byte[] encryptedData;

        public MediaInfo(MessageItemType type) {
            this.type = type;
        }
    }

    public static MediaInfo extractMedia(WeixinMessage msg) {
        if (msg == null || msg.getItemList() == null) return null;
        for (MessageItem item : msg.getItemList()) {
            MessageItemType t = item.getType();
            if (t == MessageItemType.IMAGE && item.getImageItem() != null) {
                MediaInfo info = new MediaInfo(MessageItemType.IMAGE);
                ImageItem img = item.getImageItem();
                info.aesKey = img.getAesKey();
                info.fileId = img.getFileId();
                info.fileMd5 = img.getFileMd5();
                info.fileSize = null;
                info.extension = ".jpg";
                info.encryptedData = null;
                return info;
            }
            if (t == MessageItemType.VOICE && item.getVoiceItem() != null) {
                MediaInfo info = new MediaInfo(MessageItemType.VOICE);
                VoiceItem v = item.getVoiceItem();
                info.aesKey = v.getAesKey();
                info.fileId = v.getFileId();
                info.fileMd5 = v.getFileMd5();
                info.fileSize = v.getVoiceSize();
                info.extension = ".silk";
                info.voiceText = v.getVoiceText();
                info.encryptedData = null;
                return info;
            }
            if (t == MessageItemType.VIDEO && item.getVideoItem() != null) {
                MediaInfo info = new MediaInfo(MessageItemType.VIDEO);
                VideoItem v = item.getVideoItem();
                info.aesKey = v.getAesKey();
                info.fileId = v.getFileId();
                info.fileMd5 = v.getFileMd5();
                info.fileSize = v.getVideoSize();
                info.extension = ".mp4";
                info.encryptedData = null;
                return info;
            }
            if (t == MessageItemType.FILE && item.getFileItem() != null) {
                MediaInfo info = new MediaInfo(MessageItemType.FILE);
                FileItem f = item.getFileItem();
                info.aesKey = f.getAesKey();
                info.fileId = f.getFileId();
                info.fileMd5 = f.getFileMd5();
                info.fileSize = f.getFileSize();
                info.fileName = f.getFileName();
                int dot = info.fileName != null ? info.fileName.lastIndexOf('.') : -1;
                info.extension = dot >= 0 ? info.fileName.substring(dot) : ".bin";
                info.encryptedData = null;
                return info;
            }
        }
        return null;
    }

    public static Path downloadMedia(MediaInfo media, String userId, String msgId) throws IOException {
        byte[] encrypted = media.encryptedData;
        if (encrypted == null) {
            encrypted = downloadFromCDN(media.fileId);
        }
        byte[] decrypted = aesDecrypt(encrypted, media.aesKey);
        String subDir;
        switch (media.type) {
            case IMAGE: subDir = "image"; break;
            case VOICE: subDir = "voice"; break;
            case VIDEO: subDir = "video"; break;
            case FILE: subDir = "file"; break;
            default: subDir = "other";
        }
        String fileName = (msgId != null ? msgId : System.currentTimeMillis()) + media.extension;
        Path dir = Paths.get(DOWNLOADS_DIR, userId != null ? userId : "unknown", subDir);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, decrypted);
        log.info("媒体已保存: {}", filePath);
        return filePath;
    }

    private static byte[] downloadFromCDN(String fileId) throws IOException {
        String urlStr = CDN_BASE + "?encryptQueryParam=" + fileId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream is = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally { conn.disconnect(); }
    }

    public static byte[] aesDecrypt(byte[] encrypted, String aesKeyStr) throws IOException {
        if (aesKeyStr == null || aesKeyStr.isEmpty()) return encrypted;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyStr);
            byte[] iv = new byte[16];
            System.arraycopy(keyBytes, 0, iv, 0, 16);
            byte[] aesKey = new byte[32];
            System.arraycopy(keyBytes, keyBytes.length - 32, aesKey, 0, 32);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IOException("AES decrypt failed: " + e.getMessage(), e);
        }
    }
}
