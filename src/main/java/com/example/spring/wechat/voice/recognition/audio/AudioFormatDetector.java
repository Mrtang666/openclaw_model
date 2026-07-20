package com.example.spring.wechat.voice.recognition.audio;


/**
 * 微信语音音频处理层，负责格式检测和转码。
 */
import org.springframework.stereotype.Component;

@Component
public class AudioFormatDetector {

    public String detect(String fileName, String contentType, String currentFormat) {
        if (currentFormat != null && !currentFormat.isBlank()) {
            return currentFormat.strip().toLowerCase();
        }

        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.strip().toLowerCase();
            if (normalized.contains("wav")) {
                return "wav";
            }
            if (normalized.contains("mpeg") || normalized.contains("mp3")) {
                return "mp3";
            }
            if (normalized.contains("amr")) {
                return "amr";
            }
            if (normalized.contains("silk")) {
                return "silk";
            }
        }

        if (fileName != null && fileName.contains(".")) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).strip().toLowerCase();
            if (!extension.isBlank()) {
                return extension;
            }
        }

        return "unknown";
    }
}

