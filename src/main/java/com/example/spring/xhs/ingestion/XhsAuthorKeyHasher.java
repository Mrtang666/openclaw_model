package com.example.spring.xhs.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class XhsAuthorKeyHasher {

    private final byte[] secret;

    public XhsAuthorKeyHasher(@Value("${xhs.privacy.author-hash-key:openclaw-local-only}") String secret) {
        String value = secret == null || secret.isBlank() ? "openclaw-local-only" : secret;
        this.secret = value.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String sourceAuthorId) {
        if (sourceAuthorId == null || sourceAuthorId.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(sourceAuthorId.strip().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成小红书作者关联键", exception);
        }
    }
}
