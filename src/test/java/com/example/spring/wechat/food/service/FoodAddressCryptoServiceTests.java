package com.example.spring.wechat.food.service;

import com.example.spring.wechat.food.config.FoodDeliveryProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodAddressCryptoServiceTests {

    private final FoodAddressCryptoService service = new FoodAddressCryptoService(new FoodDeliveryProperties(
            true, "https://food.example.com", "", 1000, "https://h5.ele.me",
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))));

    @Test
    void encryptsAndDecryptsAddressForSameUser() {
        String encrypted = service.encrypt("wechat-user-1", "滨江区星光大道3幢1202");

        assertThat(encrypted).doesNotContain("星光大道");
        assertThat(service.decrypt("wechat-user-1", encrypted)).isEqualTo("滨江区星光大道3幢1202");
    }

    @Test
    void rejectsCiphertextForDifferentUser() {
        String encrypted = service.encrypt("wechat-user-1", "滨江区星光大道3幢1202");

        assertThatThrownBy(() -> service.decrypt("wechat-user-2", encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
