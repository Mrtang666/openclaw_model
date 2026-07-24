package com.example.spring.wechat.netdisk.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetdiskTokenCryptoServiceTests {

    @Test
    void encryptsAndDecryptsTokenWithAuthenticatedCipher() {
        NetdiskTokenCryptoService service = new NetdiskTokenCryptoService("0123456789abcdef0123456789abcdef");

        String encrypted = service.encrypt("refresh-token-123");
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("refresh-token-123");
        assertThat(encrypted).isNotEqualTo("refresh-token-123");
    }

    @Test
    void producesDifferentCiphertextForSamePlainText() {
        NetdiskTokenCryptoService service = new NetdiskTokenCryptoService("0123456789abcdef0123456789abcdef");

        String first = service.encrypt("access-token-abc");
        String second = service.encrypt("access-token-abc");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("access-token-abc");
        assertThat(service.decrypt(second)).isEqualTo("access-token-abc");
    }

    @Test
    void rejectsBlankSecretKey() {
        assertThatThrownBy(() -> new NetdiskTokenCryptoService("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token encryption key");
    }
}
