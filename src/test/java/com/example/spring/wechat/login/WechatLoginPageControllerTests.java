package com.example.spring.wechat.login;

import com.example.spring.wechat.model.WechatLoginState;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WechatLoginPageControllerTests {

    @Test
    void returnsQrMatrixWithoutAllowingResponseCaching() {
        WechatLoginPageSessionService service = new WechatLoginPageSessionService(new WechatLoginPageProperties());
        WechatLoginPageSession session = service.create(
                "https://liteapp.weixin.qq.com/q/example?qrcode=0123456789abcdef0123456789abcdef&bot_type=3",
                () -> WechatLoginState.SCANNED);
        WechatLoginPageController controller = new WechatLoginPageController(service);

        ResponseEntity<WechatLoginPageController.LoginPageResponse> response = controller.getSession(session.id());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("SCANNED");
        assertThat(response.getBody().matrix()).hasSize(response.getBody().matrixSize());
    }

    @Test
    void returnsNotFoundForUnknownSession() {
        WechatLoginPageController controller = new WechatLoginPageController(
                new WechatLoginPageSessionService(new WechatLoginPageProperties()));

        assertThat(controller.getSession("missing").getStatusCode().value()).isEqualTo(404);
    }
}
