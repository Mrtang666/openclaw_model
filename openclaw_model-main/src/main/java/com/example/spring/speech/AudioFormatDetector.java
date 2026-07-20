package com.example.spring.speech;

import java.nio.charset.StandardCharsets;

public final class AudioFormatDetector {
    private AudioFormatDetector() {
    }

    public static String detect(byte[] data, Integer encodeType) {
        if (data != null && startsWith(data, "RIFF".getBytes(StandardCharsets.US_ASCII))
            && containsAt(data, 8, "WAVE".getBytes(StandardCharsets.US_ASCII))) {
            return "wav";
        }
        if (data != null && startsWith(data, "#!SILK_V3".getBytes(StandardCharsets.US_ASCII))) {
            return "silk";
        }
        if (data != null && startsWith(data, "OggS".getBytes(StandardCharsets.US_ASCII))) {
            return "ogg";
        }
        if (data != null && startsWith(data, "ID3".getBytes(StandardCharsets.US_ASCII))) {
            return "mp3";
        }
        if (data != null && startsWith(data, "AMR".getBytes(StandardCharsets.US_ASCII))) {
            return "amr";
        }
        if (Integer.valueOf(6).equals(encodeType)) {
            return "silk";
        }
        return "unknown";
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return containsAt(data, 0, prefix);
    }

    private static boolean containsAt(byte[] data, int offset, byte[] value) {
        if (data == null || offset < 0 || data.length - offset < value.length) {
            return false;
        }
        for (int index = 0; index < value.length; index++) {
            if (data[offset + index] != value[index]) {
                return false;
            }
        }
        return true;
    }
}
