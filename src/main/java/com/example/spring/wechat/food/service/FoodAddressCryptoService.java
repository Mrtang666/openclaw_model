package com.example.spring.wechat.food.service;

import com.example.spring.wechat.food.config.FoodDeliveryProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class FoodAddressCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final FoodDeliveryProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public FoodAddressCryptoService(FoodDeliveryProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String userKey, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(safe(userKey).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("配送地址加密失败", exception);
        }
    }

    public String decrypt(String userKey, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("配送地址密文无效");
            }
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(safe(userKey).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("配送地址解密失败", exception);
        }
    }

    private SecretKeySpec encryptionKey() {
        String configured = properties.addressEncryptionKey();
        if (configured.isBlank()) {
            throw new IllegalStateException("未配置 FOOD_DELIVERY_ADDRESS_ENCRYPTION_KEY，不能保存配送地址");
        }
        try {
            byte[] key = Base64.getDecoder().decode(configured);
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalArgumentException("地址加密密钥必须是 Base64 编码的 16、24 或 32 字节密钥");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("FOOD_DELIVERY_ADDRESS_ENCRYPTION_KEY 配置无效", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
