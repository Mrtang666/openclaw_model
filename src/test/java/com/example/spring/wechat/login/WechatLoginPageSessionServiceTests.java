package com.example.spring.wechat.login;

import com.example.spring.wechat.model.WechatLoginState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatLoginPageSessionServiceTests {

    @Test
    void createsQrMatrixAndExposesLiveLoginStatus() {
        WechatLoginPageSessionService service = new WechatLoginPageSessionService(new WechatLoginPageProperties());
        AtomicReference<WechatLoginState> status = new AtomicReference<>(WechatLoginState.WAITING);

        WechatLoginPageSession session = service.create(
                "https://liteapp.weixin.qq.com/q/example?qrcode=0123456789abcdef0123456789abcdef&bot_type=3",
                status::get);

        assertThat(session.id()).isNotBlank();
        assertThat(session.matrixSize()).isGreaterThan(20);
        assertThat(session.matrix()).hasSize(session.matrixSize());
        assertThat(session.matrix()).allMatch(row -> row.length() == session.matrixSize());
        assertThat(service.status(session)).isEqualTo(WechatLoginState.WAITING);

        status.set(WechatLoginState.SCANNED);
        assertThat(service.status(session)).isEqualTo(WechatLoginState.SCANNED);
        status.set(WechatLoginState.LOGGED_IN);
        assertThat(service.status(session)).isEqualTo(WechatLoginState.LOGGED_IN);
    }

    @Test
    void rejectsUntrustedLoginUrl() {
        WechatLoginPageSessionService service = new WechatLoginPageSessionService(new WechatLoginPageProperties());

        assertThatThrownBy(() -> service.create(
                "https://example.com/login?qrcode=test",
                () -> WechatLoginState.WAITING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("liteapp.weixin.qq.com");
    }
}
